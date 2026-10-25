package com.daniel.assistant.service

import com.daniel.assistant.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Remote control through your own Telegram bot (long polling). Only the chat that paired
 * with the one-time code shown in the app can send commands.
 */
class TelegramRemote(private val c: AppContainer) {

    private val http = OkHttpClient.Builder().readTimeout(40, TimeUnit.SECONDS).connectTimeout(20, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private var job: Job? = null

    fun attach(scope: CoroutineScope) {
        scope.launch {
            c.settings.flow.map { Triple(it.telegramEnabled, it.telegramTokenEnc, it.telegramChatId) }
                .distinctUntilChanged()
                .collect { (enabled, tokenEnc, _) ->
                    job?.cancel()
                    if (enabled && tokenEnc.isNotBlank()) job = scope.launch(Dispatchers.IO) { poll() }
                }
        }
    }

    private suspend fun poll() {
        val token = c.settings.telegramToken().ifBlank { return }
        var offset = 0L
        while (currentCoroutineContextActive()) {
            try {
                val req = Request.Builder().url("https://api.telegram.org/bot$token/getUpdates?timeout=25&offset=$offset").build()
                val body = http.newCall(req).execute().use { it.body?.string().orEmpty() }
                val result = (json.parseToJsonElement(body).jsonObject["result"] as? JsonArray).orEmpty()
                for (u in result) {
                    val o = u as? JsonObject ?: continue
                    offset = ((o["update_id"] as? JsonPrimitive)?.content?.toLongOrNull() ?: offset) + 1
                    val msg = o["message"] as? JsonObject ?: continue
                    val chatId = ((msg["chat"] as? JsonObject)?.get("id") as? JsonPrimitive)?.content ?: continue
                    val text = (msg["text"] as? JsonPrimitive)?.content?.trim() ?: continue
                    handle(token, chatId, text)
                }
            } catch (e: Exception) {
                delay(5_000)
            }
        }
    }

    private suspend fun currentCoroutineContextActive() = kotlin.coroutines.coroutineContext.isActive

    private suspend fun handle(token: String, chatId: String, text: String) {
        val s = c.settings.current()
        if (s.telegramChatId.isBlank()) {
            if (text == "/start ${s.telegramPairCode}" || text == s.telegramPairCode) {
                c.settings.setTelegramChatId(chatId)
                send(token, chatId, "✅ اتربطت بـ Nabra. ابعتلي أي أمر زي ما بتقوله بصوتك.\n/stop لإيقاف أي حاجة شغالة")
                c.log.ok("تيليجرام: اتربط")
            } else {
                send(token, chatId, "ابعت كود الربط اللي ظاهر في إعدادات Nabra.")
            }
            return
        }
        if (chatId != s.telegramChatId) return // ignore strangers
        when (text) {
            "/stop" -> withContext(Dispatchers.Main) { c.brain.cancelAll() }.also { send(token, chatId, "⏹ وقفت") }
            "/start", "/help" -> send(token, chatId, "ابعت أي أمر: \"واحد\"، \"افتح يوتيوب\"، \"ابعت لأحمد على الواتس انا جاي\"…")
            else -> {
                c.log.info("تيليجرام: \"$text\"")
                withContext(Dispatchers.Main) { c.brain.handle(text, grammarMode = false) }
                delay(1_200)
                send(token, chatId, "▶ " + c.brain.lastResult.value.ifBlank { "تمام" })
            }
        }
    }

    private fun send(token: String, chatId: String, text: String) {
        runCatching {
            http.newCall(
                Request.Builder().url("https://api.telegram.org/bot$token/sendMessage")
                    .post(FormBody.Builder().add("chat_id", chatId).add("text", text).build()).build()
            ).execute().close()
        }
    }
}
