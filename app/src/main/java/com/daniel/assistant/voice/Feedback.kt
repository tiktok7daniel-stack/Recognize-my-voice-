package com.daniel.assistant.voice

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.daniel.assistant.data.CueMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/** Vibration / beep cues and short spoken confirmations via Android TTS. */
class Feedback(private val context: Context) {

    @Volatile var ttsEnabled = true
    @Volatile var cueMode = CueMode.BOTH

    private val _speaking = MutableStateFlow(false)
    /** True while TTS is talking — the listener pauses so it doesn't hear itself. */
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val tone by lazy { runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) }.getOrNull() }

    private fun ensureTts() {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val t = tts ?: return@TextToSpeech
                val eg = Locale("ar", "EG")
                val res = t.setLanguage(eg)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    t.setLanguage(Locale("ar"))
                }
                t.setSpeechRate(1.05f)
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { _speaking.value = true }
                    override fun onDone(utteranceId: String?) { _speaking.value = false }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { _speaking.value = false }
                    override fun onError(utteranceId: String?, errorCode: Int) { _speaking.value = false }
                })
                ttsReady = true
                pending?.let { say(it) }
                pending = null
            }
        }
    }

    private var pending: String? = null

    fun warmUp() { if (ttsEnabled) ensureTts() }

    fun say(text: String) {
        if (!ttsEnabled || text.isBlank()) return
        ensureTts()
        if (!ttsReady) {
            pending = text; return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), UUID.randomUUID().toString())
    }

    /** Short cue when the assistant starts listening for a command. */
    fun cue() {
        if (cueMode != CueMode.BEEP) vibrate(60)
        if (cueMode != CueMode.VIBRATE) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
    }

    fun errorCue() {
        if (cueMode != CueMode.BEEP) vibrate(200)
        if (cueMode != CueMode.VIBRATE) tone?.startTone(ToneGenerator.TONE_PROP_NACK, 200)
    }

    private fun vibrate(ms: Long) {
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        v?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ttsReady = false
    }
}
