package com.daniel.assistant.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.beta.messages.MessageCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Duration
import java.util.concurrent.TimeUnit

/** A text-in / text-out model backend used by the AI agent. */
interface AiProvider {
    suspend fun complete(system: String, user: String): String
}

class AiException(message: String) : Exception(message)

enum class ProviderKind(val id: String, val title: String) {
    ANTHROPIC("anthropic", "Claude (Anthropic)"),
    OPENAI_COMPATIBLE("openai", "أي API متوافق مع OpenAI / سيرفر محلي"),
    ON_DEVICE("ondevice", "موديل جوه الموبايل (Gemma — من غير نت)");

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id } ?: ANTHROPIC
    }
}

object AiDefaults {
    const val ANTHROPIC_MODEL = "claude-opus-5-5"
    const val OPENAI_BASE_URL = "http://127.0.0.1:8080/v1"
    const val OPENAI_MODEL = "local-model"
}

/** Claude via the official Anthropic Java SDK. */
class AnthropicProvider(apiKey: String, private val model: String) : AiProvider {

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .timeout(Duration.ofSeconds(90))
        .build()

    // Models that support server-side refusal fallbacks ("fallbacks":"default").
    private val supportsFallbacks = model in setOf(
        "claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5", "claude-fable-5-1",
    )

    override suspend fun complete(system: String, user: String): String = withContext(Dispatchers.IO) {
        val builder = MessageCreateParams.builder()
            .model(model)
            .maxTokens(16000L)
            .system(system)
            .addUserMessage(user)
        if (supportsFallbacks) {
            builder.addBeta("server-side-fallback-2026-07-01")
            builder.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        }
        val message = client.beta().messages().create(builder.build())
        val stop = message.stopReason().map { it.toString() }.orElse("")
        if (stop == "refusal") throw AiException("الموديل رفض الطلب")
        message.content()
            .mapNotNull { block -> block.text().map { it.text() }.orElse(null) }
            .joinToString("")
    }
}

/**
 * Any OpenAI-compatible Chat Completions endpoint: OpenAI, OpenRouter, Groq,
 * Gemini's OpenAI endpoint, or a local model server running on the phone
 * (llama.cpp server / Ollama in Termux at http://127.0.0.1:PORT/v1).
 */
class OpenAiCompatibleProvider(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) : AiProvider {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun complete(system: String, user: String): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", model)
            put("temperature", 0)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            })
        }
        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val req = Request.Builder()
            .url(url)
            .apply { if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey") }
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw AiException("HTTP ${resp.code}: ${text.take(200)}")
            val root = json.parseToJsonElement(text).jsonObject
            val choices = root["choices"] as? JsonArray ?: throw AiException("رد غير متوقع")
            val msg = choices.firstOrNull()?.jsonObject?.get("message") as? JsonObject
            (msg?.get("content") as? JsonPrimitive)?.content ?: throw AiException("رد فاضي")
        }
    }
}
