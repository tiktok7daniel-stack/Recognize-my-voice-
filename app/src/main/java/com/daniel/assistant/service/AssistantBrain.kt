package com.daniel.assistant.service

import com.daniel.assistant.AppContainer
import com.daniel.assistant.commands.CommandResolver
import com.daniel.assistant.commands.Resolution
import com.daniel.assistant.commands.SysAction
import com.daniel.assistant.voice.ArabicNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Turns a heard (or typed) command into an action, following the resolution priority. */
class AssistantBrain(private val c: AppContainer) {

    private val _lastHeard = MutableStateFlow("")
    val lastHeard: StateFlow<String> = _lastHeard.asStateFlow()

    private val _lastResult = MutableStateFlow("")
    val lastResult: StateFlow<String> = _lastResult.asStateFlow()

    private val _listening = MutableStateFlow(false)
    /** True while the accurate command recognizer is listening (after wake word / bubble / tile). */
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial.asStateFlow()

    fun setHeard(text: String) { _lastHeard.value = text }

    fun cancelAll() {
        c.executor.cancel()
        c.ai.cancel()
    }

    suspend fun usesGoogle(): Boolean =
        c.settings.current().commandEngine == "google" && c.google.isAvailable()

    /** Phrases that bias the online recognizer toward things the user actually says. */
    private suspend fun biasPhrases(): List<String> {
        val out = mutableListOf<String>()
        c.repo.all().filter { it.enabled }.forEach { out.addAll(it.triggers) }
        SysAction.entries.forEach { out.addAll(it.phrases) }
        c.appEntries().forEach { app ->
            app.spokenNames.filter { ArabicNormalizer.isArabic(it) }.take(2).forEach { out.add("افتح $it") }
        }
        return out
    }

    private suspend fun waitSpeechEnd() {
        withTimeoutOrNull(3_000) { c.feedback.speaking.first { it } }
        withTimeoutOrNull(8_000) { c.feedback.speaking.first { !it } }
    }

    /**
     * Listens for one command with the accurate recognizer and runs it.
     * Returns false when that recognizer isn't enabled/available, so the caller can fall back
     * to the offline listener's command window.
     */
    suspend fun listenForCommand(): Boolean {
        if (!usesGoogle()) return false
        _listening.value = true
        _partial.value = ""
        val alts = try {
            c.voice.withMicReleased { c.google.capture(biasPhrases(), 9_000) { _partial.value = it } }
        } finally {
            _listening.value = false
            _partial.value = ""
        }
        if (alts.isNullOrEmpty() && c.google.lastErrorIsHard) {
            // Online recognizer unavailable (no network / busy / blocked) → offline fallback.
            c.log.info("التعرف الأونلاين مش متاح (كود ${c.google.lastError}) — بستخدم الأوفلاين")
            if (c.voice.loadModel()) {
                _listening.value = true
                val text = try {
                    c.voice.captureOnce(null, 7_000) { _partial.value = it }
                } finally {
                    _listening.value = false; _partial.value = ""
                }
                if (!text.isNullOrBlank()) {
                    handleAlternatives(listOf(text), grammarMode = false)
                    return true
                }
            }
        }
        if (alts.isNullOrEmpty()) {
            c.feedback.say("مسمعتش")
            result("مسمعتش حاجة")
            return true
        }
        handleAlternatives(alts, grammarMode = false)
        return true
    }

    /** Captures free text (for "اكتب …", AI commands). */
    private suspend fun captureFreeText(timeoutMs: Long): String? =
        if (usesGoogle()) {
            _listening.value = true
            try {
                c.voice.withMicReleased { c.google.capture(timeoutMs = timeoutMs) { _partial.value = it } }?.firstOrNull()
            } finally {
                _listening.value = false; _partial.value = ""
            }
        } else c.voice.captureOnce(null, timeoutMs)

    suspend fun handle(heard: String, grammarMode: Boolean) = handleAlternatives(listOf(heard), grammarMode)

    /**
     * @param grammarMode true when the recognizer is restricted to a grammar, so free text
     * (AI commands, "اكتب ...") must be captured again in free-form mode.
     */
    suspend fun handleAlternatives(alternatives: List<String>, grammarMode: Boolean) {
        val s = c.settings.current()
        val resolver = CommandResolver(customThreshold = s.threshold.toDouble())
        val aiAvailable = c.ai.isAvailable()
        val top = alternatives.firstOrNull().orEmpty()
        val norm = ArabicNormalizer.normalize(top)

        val (heard, res) = if (alternatives.size == 1 && (norm.isBlank() || norm == "[unk]") && grammarMode && aiAvailable) {
            top to Resolution.Ai("")
        } else {
            resolver.resolveBest(alternatives, c.repo.all(), c.appEntries(), aiAvailable)
        }
        _lastHeard.value = heard.ifBlank { top }
        execute(res, heard.ifBlank { top }, grammarMode)
    }

    private suspend fun execute(res: Resolution, heard: String, grammarMode: Boolean) {
        when (res) {
            is Resolution.Custom -> {
                result("أمر: ${res.command.name}")
                c.executor.run(res.command)
            }
            is Resolution.System -> {
                result("نظام: ${res.action.title}")
                if (res.action !in setOf(SysAction.LOCK, SysAction.SCREENSHOT)) c.feedback.say("حاضر")
                val err = c.executor.runSystem(res.action, res.arg)
                if (err != null) {
                    c.feedback.say(err); c.log.error("${res.action.title}: $err")
                } else c.log.ok("نظام: ${res.action.title}${res.arg?.let { " ($it)" } ?: ""}")
            }
            is Resolution.NeedsArg -> {
                c.feedback.say(if (res.action == SysAction.CLICK) "أضغط على إيه؟" else "أكتب إيه؟")
                waitSpeechEnd()
                val arg = captureFreeText(8_000)
                if (arg.isNullOrBlank()) {
                    c.feedback.say("مسمعتش"); return
                }
                _lastHeard.value = "$heard $arg"
                val err = c.executor.runSystem(res.action, arg)
                if (err != null) c.feedback.say(err) else c.log.ok("${res.action.title}: $arg")
            }
            is Resolution.OpenApp -> {
                result("افتح ${res.app.label}")
                c.feedback.say("حاضر")
                val err = c.executor.openApp(res.app.packageName, res.app.label)
                if (err != null) {
                    c.feedback.say(err); c.log.error(err)
                } else c.log.ok("فتح ${res.app.label}")
            }
            is Resolution.OpenAppUnknown -> {
                result("تطبيق مش معروف: ${res.name}")
                c.feedback.say("مش لاقي التطبيق ده")
                c.notifier.notUnderstood(heard)
            }
            is Resolution.Ai -> {
                var command = res.text
                if (grammarMode || command.isBlank()) {
                    c.feedback.say("قول الأمر")
                    waitSpeechEnd()
                    command = captureFreeText(9_000).orEmpty()
                    if (command.isBlank()) {
                        c.feedback.say("مسمعتش"); return
                    }
                    _lastHeard.value = command
                    if (SysAction.CANCEL_WORDS.any { ArabicNormalizer.normalize(it) == ArabicNormalizer.normalize(command) }) return
                }
                result("ذكاء اصطناعي: $command")
                c.ai.start(command)
            }
            is Resolution.Cancel -> {
                cancelAll()
                c.feedback.say("وقفت")
                result("إلغاء")
            }
            is Resolution.Unknown -> {
                result("مفهمتش")
                c.feedback.errorCue()
                c.feedback.say("مفهمتش")
                c.notifier.notUnderstood(heard)
                c.log.info("مش مفهوم: \"$heard\"")
            }
        }
    }

    private fun result(text: String) { _lastResult.value = text }
}
