package com.daniel.assistant.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Runs a local LLM (e.g. Gemma .task file) fully on the phone with MediaPipe — no internet,
 * no Termux. The engine is loaded once and reused.
 */
class OnDeviceProvider(private val context: Context, private val modelPath: String) : AiProvider {

    override suspend fun complete(system: String, user: String): String = withContext(Dispatchers.Default) {
        val llm = engine(context, modelPath)
        val prompt = "$system\n\n$user\n\nReply with ONE JSON object only.\n"
        llm.generateResponse(prompt)
    }

    companion object {
        private val lock = Mutex()
        @Volatile private var engine: LlmInference? = null
        @Volatile private var loadedPath: String? = null

        fun modelFile(context: Context) = File(context.filesDir, "llm/model.task")

        private suspend fun engine(context: Context, path: String): LlmInference = lock.withLock {
            engine?.takeIf { loadedPath == path }?.let { return it }
            engine?.close()
            if (!File(path).exists()) throw AiException("ملف الموديل المحلي مش موجود")
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(path)
                .setMaxTokens(1024)
                .build()
            LlmInference.createFromOptions(context, options).also {
                engine = it
                loadedPath = path
            }
        }

        fun release() {
            engine?.close()
            engine = null
            loadedPath = null
        }
    }
}
