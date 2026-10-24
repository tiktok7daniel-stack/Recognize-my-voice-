package com.daniel.assistant.ai

import android.accessibilityservice.AccessibilityService
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.daniel.assistant.accessibility.AssistantAccessibilityService
import com.daniel.assistant.accessibility.NodeActions
import com.daniel.assistant.accessibility.ScreenSerializer
import com.daniel.assistant.commands.AppEntry
import com.daniel.assistant.commands.CommandResolver
import com.daniel.assistant.commands.SysAction
import com.daniel.assistant.data.ExecLog
import com.daniel.assistant.data.SettingsRepo
import com.daniel.assistant.executor.CommandExecutor
import com.daniel.assistant.service.Notifier
import com.daniel.assistant.voice.ArabicNormalizer
import com.daniel.assistant.voice.Feedback
import com.daniel.assistant.voice.GrammarBuilder
import com.daniel.assistant.voice.VoiceEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * AI mode: reads the screen, asks the model for ONE action, executes it,
 * waits for the UI to settle and repeats (max 15 steps).
 */
class AiAgent(
    private val context: Context,
    private val settings: SettingsRepo,
    private val feedback: Feedback,
    private val notifier: Notifier,
    private val log: ExecLog,
    private val voice: VoiceEngine,
    private val executor: CommandExecutor,
    private val scope: CoroutineScope,
    private val appsProvider: suspend () -> List<AppEntry>,
) {
    private var job: Job? = null
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** AI is usable when enabled and (for remote providers) online. Local servers work offline. */
    suspend fun isAvailable(): Boolean {
        val s = settings.current()
        if (!s.aiEnabled) return false
        val local = s.aiProvider == ProviderKind.OPENAI_COMPATIBLE.id &&
            (s.aiBaseUrl.contains("127.0.0.1") || s.aiBaseUrl.contains("localhost"))
        return local || isOnline()
    }

    fun cancel() {
        job?.cancel()
        job = null
        _running.value = false
        ConfirmationCenter.answer(false)
        notifier.cancel(Notifier.ID_CONFIRM)
    }

    fun start(command: String) {
        cancel()
        job = scope.launch { run(command) }
    }

    private suspend fun buildProvider(): AiProvider? {
        val s = settings.current()
        val key = settings.apiKey()
        return when (ProviderKind.byId(s.aiProvider)) {
            ProviderKind.ANTHROPIC -> {
                if (key.isBlank()) null else AnthropicProvider(key, s.aiModel)
            }
            ProviderKind.OPENAI_COMPATIBLE -> OpenAiCompatibleProvider(s.aiBaseUrl, key, s.aiModel)
        }
    }

    suspend fun run(command: String) {
        _running.value = true
        log.info("AI: \"$command\"")
        try {
            val provider = buildProvider() ?: run {
                feedback.say("حط مفتاح الـ API في الإعدادات الأول")
                log.error("AI: مفيش مفتاح API")
                return
            }
            if (AssistantAccessibilityService.get() == null) {
                notifier.accessibilityDisabled()
                feedback.say("فعّل خدمة إمكانية الوصول الأول")
                return
            }
            feedback.say("ماشي")
            val history = mutableListOf<String>()
            val confirmSensitive = settings.current().aiConfirmSensitive
            for (stepNo in 1..MAX_STEPS) {
                val service = AssistantAccessibilityService.get() ?: run {
                    notifier.accessibilityDisabled(); return
                }
                val snap = ScreenSerializer.snapshot(service.root())
                if (SafetyGuard.isBlockedApp(snap.packageName)) {
                    feedback.say("مش هشتغل جوه التطبيق ده عشان الأمان")
                    log.error("AI: وقف في تطبيق محمي ${snap.packageName}")
                    return
                }
                val reply = try {
                    provider.complete(AiPrompts.SYSTEM, AiPrompts.userTurn(command, history, snap.text))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    feedback.say("مقدرتش أوصل للذكاء الاصطناعي")
                    log.error("AI: ${e.message}")
                    return
                }
                val action = AiAction.parse(reply)
                if (action == null) {
                    history.add("(invalid reply, answer with ONE JSON action)")
                    continue
                }
                if (action is AiAction.Done) {
                    feedback.say(action.say.ifBlank { "خلصت" })
                    log.ok("AI خلص في $stepNo خطوة: ${action.say}")
                    return
                }
                val label = (action as? AiAction.Click)?.let { a ->
                    snap.nodes.getOrNull(a.index)?.let { n -> "${n.text ?: ""} ${n.contentDescription ?: ""}" }
                }
                val needsConfirm = confirmSensitive && (
                    action.sensitive || SafetyGuard.looksSensitive(label) ||
                        (SafetyGuard.isSettings(snap.packageName) && (action is AiAction.Click || action is AiAction.Type))
                    )
                if (needsConfirm) {
                    val what = describe(action, label)
                    if (!confirm("أأكد: $what؟")) {
                        feedback.say("تمام، لغيت")
                        log.info("AI: المستخدم لغى \"$what\"")
                        return
                    }
                }
                val err = execute(service, action, snap)
                history.add(describe(action, label) + if (err != null) " -> FAILED: $err" else " -> ok")
                settle()
            }
            feedback.say("وقفت بعد ١٥ خطوة")
            log.error("AI: وصل للحد الأقصى ($MAX_STEPS خطوة)")
        } finally {
            _running.value = false
        }
    }

    private fun describe(a: AiAction, label: String?): String = when (a) {
        is AiAction.Click -> "click #${a.index} ${label?.trim().orEmpty()}"
        is AiAction.Type -> "type \"${a.text}\" in #${a.index}"
        is AiAction.OpenApp -> "open app ${a.name}"
        is AiAction.Scroll -> "scroll ${if (a.down) "down" else "up"}"
        is AiAction.Back -> "back"
        is AiAction.Home -> "home"
        is AiAction.Wait -> "wait ${a.millis}ms"
        is AiAction.Done -> "done"
    }

    private suspend fun execute(
        service: AssistantAccessibilityService,
        a: AiAction,
        snap: ScreenSerializer.Snapshot,
    ): String? = when (a) {
        is AiAction.Click -> snap.nodes.getOrNull(a.index)?.let { n ->
            if (NodeActions.clickNode(service, n)) null else "click failed"
        } ?: "bad index"
        is AiAction.Type -> {
            val n = snap.nodes.getOrNull(a.index)?.takeIf { it.isEditable } ?: NodeActions.findEditableNode(service.root())
            if (n == null) "no editable field" else if (NodeActions.setText(n, a.text)) null else "type failed"
        }
        is AiAction.OpenApp -> {
            val app = CommandResolver().findApp(a.name, appsProvider())
            if (app == null) "app not found" else executor.openApp(app.app.packageName, app.app.label)
        }
        is AiAction.Scroll -> if (NodeActions.scroll(service, a.down)) null else "cannot scroll"
        is AiAction.Back -> if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) null else "failed"
        is AiAction.Home -> if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) null else "failed"
        is AiAction.Wait -> { delay(a.millis.coerceIn(100, 5_000)); null }
        is AiAction.Done -> null
    }

    /** Waits until accessibility events quiet down for 500 ms (max 2 s). */
    private suspend fun settle() {
        delay(300)
        val end = SystemClock.uptimeMillis() + 2_000
        while (SystemClock.uptimeMillis() < end) {
            if (SystemClock.uptimeMillis() - AssistantAccessibilityService.lastEventAt > 500) return
            delay(100)
        }
    }

    /** Asks by voice ("أيوه"/"لا") and by notification; whichever answers first wins. */
    private suspend fun confirm(question: String): Boolean {
        val deferred = ConfirmationCenter.begin()
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val yes = PendingIntent.getBroadcast(
            context, 21, Intent(context, ConfirmReceiver::class.java).setAction(ConfirmationCenter.ACTION_CONFIRM), flags,
        )
        val no = PendingIntent.getBroadcast(
            context, 22, Intent(context, ConfirmReceiver::class.java).setAction(ConfirmationCenter.ACTION_CANCEL), flags,
        )
        notifier.confirm(question, yes, no)
        feedback.say(question)
        val voiceJob = scope.launch {
            withTimeoutOrNull(4_000) { feedback.speaking.first { it } }
            withTimeoutOrNull(10_000) { feedback.speaking.first { !it } }
            val words = SysAction.YES_WORDS + SysAction.NO_WORDS
            val heard = voice.captureOnce(GrammarBuilder.toJson(words), 7_000) ?: return@launch
            val n = ArabicNormalizer.normalize(heard)
            fun hit(list: List<String>) = list.any { w ->
                val nw = ArabicNormalizer.normalize(w)
                n == nw || n.split(' ').contains(nw)
            }
            when {
                hit(SysAction.NO_WORDS) -> ConfirmationCenter.answer(false)
                hit(SysAction.YES_WORDS) -> ConfirmationCenter.answer(true)
            }
        }
        val answer = withTimeoutOrNull(30_000) { deferred.await() } ?: false
        voiceJob.cancel()
        notifier.cancel(Notifier.ID_CONFIRM)
        return answer
    }

    companion object {
        const val MAX_STEPS = 15
    }
}
