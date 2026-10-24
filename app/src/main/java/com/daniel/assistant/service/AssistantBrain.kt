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

    fun setHeard(text: String) { _lastHeard.value = text }

    fun cancelAll() {
        c.executor.cancel()
        c.ai.cancel()
    }

    private suspend fun waitSpeechEnd() {
        withTimeoutOrNull(3_000) { c.feedback.speaking.first { it } }
        withTimeoutOrNull(8_000) { c.feedback.speaking.first { !it } }
    }

    /**
     * @param grammarMode true when the recognizer is restricted to a grammar, so free text
     * (AI commands, "اكتب ...") must be captured again in free-form mode.
     */
    suspend fun handle(heard: String, grammarMode: Boolean) {
        _lastHeard.value = heard
        val s = c.settings.current()
        val resolver = CommandResolver(customThreshold = s.threshold.toDouble())
        val aiAvailable = c.ai.isAvailable()
        val norm = ArabicNormalizer.normalize(heard)

        val res = if ((norm.isBlank() || norm == "[unk]") && grammarMode && aiAvailable) {
            Resolution.Ai("")
        } else {
            resolver.resolve(heard, c.repo.all(), c.appEntries(), aiAvailable)
        }

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
                val arg = c.voice.captureOnce(null, 8_000)
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
                    command = c.voice.captureOnce(null, 9_000).orEmpty()
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
