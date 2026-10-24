package com.daniel.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Android's built-in speech recognizer (Google) for the command itself: far more accurate
 * for Egyptian Arabic than a generic offline model, and returns several alternatives that
 * the resolver can choose from. Uses the on-device language pack when available.
 * Must be used from the main thread.
 */
class GoogleRecognizer(private val context: Context) {

    private val _level = MutableStateFlow(0f)
    /** Normalized microphone level (0..1) while listening — drives the UI animation. */
    val level: StateFlow<Float> = _level.asStateFlow()

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /** Error code of the last failed capture (SpeechRecognizer.ERROR_*), or null. */
    @Volatile var lastError: Int? = null
        private set

    /** True when the last failure means "the recognizer can't work right now" (not just silence). */
    val lastErrorIsHard: Boolean
        get() = lastError.let { it != null && it != SpeechRecognizer.ERROR_NO_MATCH && it != SpeechRecognizer.ERROR_SPEECH_TIMEOUT }

    /**
     * Listens for one utterance. Returns up to 5 alternatives (best first), or null on
     * silence / error / timeout.
     */
    suspend fun capture(
        bias: List<String> = emptyList(),
        timeoutMs: Long = 9_000,
        language: String = "ar-EG",
        onPartial: (String) -> Unit = {},
    ): List<String>? = withTimeoutOrNull(timeoutMs) {
        lastError = null
        suspendCancellableCoroutine { cont ->
            val sr = SpeechRecognizer.createSpeechRecognizer(context)
            var done = false
            fun finish(v: List<String>?) {
                if (done) return
                done = true
                _level.value = 0f
                runCatching { sr.destroy() }
                if (cont.isActive) cont.resume(v?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() })
            }
            sr.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    _level.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { _level.value = 0f }
                override fun onError(error: Int) {
                    lastError = error
                    finish(null)
                }
                override fun onResults(results: Bundle?) =
                    finish(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(onPartial)
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                if (Build.VERSION.SDK_INT >= 33 && bias.isNotEmpty()) {
                    // Nudges recognition toward the user's own triggers and app names.
                    putExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(bias.distinct().take(200)))
                }
            }
            try {
                sr.startListening(intent)
            } catch (e: Exception) {
                lastError = SpeechRecognizer.ERROR_CLIENT
                finish(null)
            }
            cont.invokeOnCancellation {
                android.os.Handler(android.os.Looper.getMainLooper()).post { finish(null) }
            }
        }
    }
}
