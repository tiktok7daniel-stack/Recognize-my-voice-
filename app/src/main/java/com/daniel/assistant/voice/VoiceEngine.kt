package com.daniel.assistant.voice

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import kotlin.coroutines.resume

/**
 * Owns the single microphone stream and the Vosk model.
 * - [startContinuous] runs the always-on listener (grammar or free-form).
 * - [captureOnce] temporarily takes over the mic for one utterance (trigger
 *   recording, AI command, confirmations) and then restores the continuous mode.
 * All public functions must be called from the main thread.
 */
class VoiceEngine(private val models: ModelManager) {

    data class Continuous(
        val grammar: String?,
        val onResult: (String) -> Unit,
        val onPartial: (String) -> Unit = {},
        val onError: (Exception) -> Unit = {},
    )

    private var model: Model? = null
    private var speech: SpeechService? = null
    private var recognizer: Recognizer? = null
    private var continuous: Continuous? = null
    private val mutex = Mutex()
    private var paused = false

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    /** Speaker (voice-print) model; when set, results carry a voice vector. */
    @Volatile var speakerModel: org.vosk.SpeakerModel? = null

    /** Voice-print vector of the most recent final result (null if unavailable). */
    @Volatile var lastSpeakerVector: FloatArray? = null
        private set

    /** Whether the running continuous recognizer uses a grammar. */
    var grammarActive: Boolean = false
        private set

    val modelLoaded: Boolean get() = model != null

    suspend fun loadModel(): Boolean = withContext(Dispatchers.IO) {
        if (model != null) return@withContext true
        if (!models.isReady()) return@withContext false
        try {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            model = Model(models.modelDir.absolutePath)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "model load failed", e)
            false
        }
    }

    fun supportsGrammar(): Boolean = models.supportsGrammar()

    suspend fun startContinuous(cfg: Continuous) = mutex.withLock {
        continuous = cfg
        stopInternal()
        startInternal(cfg)
    }

    suspend fun stopContinuous() = mutex.withLock {
        continuous = null
        stopInternal()
    }

    fun setPaused(p: Boolean) {
        paused = p
        speech?.setPause(p)
        _listening.value = speech != null && !p
    }

    /**
     * Listens for one utterance and returns its text (null on timeout/silence).
     * Pass a grammar JSON to restrict the vocabulary (e.g. yes/no), or null for free-form.
     */
    suspend fun captureOnce(grammar: String?, timeoutMs: Long, onPartial: (String) -> Unit = {}): String? =
        mutex.withLock {
            val m = model ?: return@withLock null
            stopInternal()
            val rec = withContext(Dispatchers.Default) { createRecognizer(m, grammar) }
            var svc: SpeechService? = null
            val result = try {
                withTimeoutOrNull(timeoutMs + 2_000) {
                    suspendCancellableCoroutine<String?> { cont ->
                        var lastPartial = ""
                        fun finish(v: String?) {
                            if (cont.isActive) cont.resume(v?.takeIf { it.isNotBlank() && it != "[unk]" })
                        }
                        val s = SpeechService(rec, SAMPLE_RATE)
                        svc = s
                        s.startListening(object : RecognitionListener {
                            override fun onPartialResult(hypothesis: String?) {
                                val p = parse(hypothesis, "partial")
                                if (p.isNotBlank()) {
                                    lastPartial = p; onPartial(p)
                                }
                            }
                            override fun onResult(hypothesis: String?) {
                                val t = parse(hypothesis, "text")
                                if (t.isNotBlank() && t != "[unk]") {
                                    captureSpeaker(hypothesis); finish(t)
                                }
                            }
                            override fun onFinalResult(hypothesis: String?) {
                                captureSpeaker(hypothesis)
                                finish(parse(hypothesis, "text").ifBlank { lastPartial })
                            }
                            override fun onError(exception: Exception?) = finish(null)
                            override fun onTimeout() = finish(lastPartial)
                        }, timeoutMs.toInt())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "capture failed", e)
                null
            } finally {
                svc?.let { it.stop(); it.shutdown() }
                rec.close()
            }
            continuous?.let { startInternal(it) }
            result
        }

    /**
     * Stops the continuous listener so another recognizer (e.g. Google) can use the
     * microphone, runs [block], then restores continuous listening.
     */
    suspend fun <T> withMicReleased(block: suspend () -> T): T = mutex.withLock {
        stopInternal()
        try {
            block()
        } finally {
            continuous?.let { startInternal(it) }
        }
    }

    fun release() {
        stopInternal()
        continuous = null
        model?.close()
        model = null
    }

    private fun createRecognizer(m: Model, grammar: String?): Recognizer {
        val rec = if (grammar != null) {
            try {
                Recognizer(m, SAMPLE_RATE, grammar)
            } catch (e: Exception) {
                Log.w(TAG, "grammar rejected, using free-form", e)
                Recognizer(m, SAMPLE_RATE)
            }
        } else Recognizer(m, SAMPLE_RATE)
        speakerModel?.let { spk -> runCatching { rec.setSpeakerModel(spk) } }
        return rec
    }

    private fun captureSpeaker(json: String?) {
        lastSpeakerVector = runCatching {
            val arr = JSONObject(json ?: "{}").optJSONArray("spk") ?: return@runCatching null
            FloatArray(arr.length()) { arr.getDouble(it).toFloat() }
        }.getOrNull()
    }

    private suspend fun startInternal(cfg: Continuous) {
        val m = model ?: return
        val rec = withContext(Dispatchers.Default) { createRecognizer(m, cfg.grammar) }
        val svc = try {
            SpeechService(rec, SAMPLE_RATE)
        } catch (e: Exception) {
            rec.close()
            cfg.onError(e)
            return
        }
        svc.startListening(object : RecognitionListener {
            override fun onPartialResult(hypothesis: String?) {
                val p = parse(hypothesis, "partial")
                if (p.isNotBlank()) cfg.onPartial(p)
            }
            override fun onResult(hypothesis: String?) {
                val t = parse(hypothesis, "text")
                if (t.isNotBlank()) {
                    captureSpeaker(hypothesis); cfg.onResult(t)
                }
            }
            override fun onFinalResult(hypothesis: String?) {
                val t = parse(hypothesis, "text")
                if (t.isNotBlank()) {
                    captureSpeaker(hypothesis); cfg.onResult(t)
                }
            }
            override fun onError(exception: Exception?) {
                cfg.onError(exception ?: RuntimeException("recognizer error"))
            }
            override fun onTimeout() {}
        })
        if (paused) svc.setPause(true)
        speech = svc
        recognizer = rec
        grammarActive = cfg.grammar != null
        _listening.value = !paused
    }

    private fun stopInternal() {
        speech?.let {
            it.stop()
            it.shutdown()
        }
        speech = null
        recognizer?.close()
        recognizer = null
        _listening.value = false
    }

    private fun parse(json: String?, key: String): String {
        val raw = runCatching { JSONObject(json ?: "{}").optString(key, "") }.getOrDefault("").trim()
        return ArabicNormalizer.fromBuckwalterIfNeeded(raw)
    }

    companion object {
        private const val TAG = "VoiceEngine"
        const val SAMPLE_RATE = 16000f
    }
}
