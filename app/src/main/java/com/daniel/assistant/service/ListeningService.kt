package com.daniel.assistant.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.daniel.assistant.AppContainer
import com.daniel.assistant.NabraApp
import com.daniel.assistant.R
import com.daniel.assistant.commands.SysAction
import com.daniel.assistant.ui.MainActivity
import com.daniel.assistant.voice.ArabicNormalizer
import com.daniel.assistant.voice.FuzzyMatcher
import com.daniel.assistant.voice.GrammarBuilder
import com.daniel.assistant.voice.VoiceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service (type=microphone) that keeps Vosk listening for the wake word,
 * opens a ~6 s command window and hands commands to [AssistantBrain].
 */
private data class ListenKey(
    val wakeWord: String,
    val useGrammar: Boolean,
    val pauseOnScreenOff: Boolean,
    val onlineCommands: Boolean,
    val voiceLock: Boolean,
)

class ListeningService : Service() {

    data class State(
        val running: Boolean = false,
        val paused: Boolean = false,
        val awaitingCommand: Boolean = false,
        val grammarMode: Boolean = false,
        val partial: String = "",
        val error: String? = null,
    )

    companion object {
        const val ACTION_START = "com.daniel.assistant.START"
        const val ACTION_PAUSE = "com.daniel.assistant.PAUSE"
        const val ACTION_RESUME = "com.daniel.assistant.RESUME"
        const val ACTION_STOP = "com.daniel.assistant.STOP"
        const val ACTION_LISTEN_NOW = "com.daniel.assistant.LISTEN_NOW"
        private const val COMMAND_WINDOW_MS = 6_500L

        private val _state = MutableStateFlow(State())
        @Volatile private var lastBlockedPrompt = 0L
        val state: StateFlow<State> = _state.asStateFlow()

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, ListeningService::class.java).setAction(ACTION_START)
            )
        }

        /** Start (if needed) and immediately listen for one command, no wake word. */
        fun listenNow(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, ListeningService::class.java).setAction(ACTION_LISTEN_NOW)
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ListeningService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var c: AppContainer
    private var started = false
    private var listenJob: Job? = null
    private var windowJob: Job? = null
    private var userPaused = false
    private var screenOff = false
    private var speaking = false
    private var wakeWord = ""
    private var pauseOnScreenOff = true
    private var directTriggers = true
    private var voicePrint: FloatArray? = null
    private var voiceThreshold = 0.55f
    private var cachedCommands: List<com.daniel.assistant.commands.CustomCommand> = emptyList()
    private var pendingListenNow = false
    private var modelReady = false
    /** Wake word was already acted on from a partial result; skip it in the final one. */
    private var wakeFromPartial = false
    private var wakeFromPartialAt = 0L
    private var instantTriggers = true
    private var instantDelayMs = 150L
    private var instantJob: Job? = null
    /** Trigger already fired from a partial result; its final result must be ignored. */
    private var firedInstant: String? = null
    private var firedInstantAt = 0L
    private lateinit var bubble: FloatingBubble

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOff = intent.action == Intent.ACTION_SCREEN_OFF
            applyPause()
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            scope.launch { c.appIndex.refresh() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        c = (application as NabraApp).container
        bubble = FloatingBubble(this) { listenNowInternal() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // Explicit stop by the user: don't auto-restart until the app is opened again.
                c.scope.launch { c.settings.setManualStop(true) }
                stopEverything()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> { userPaused = true; applyPause() }
            ACTION_RESUME -> { userPaused = false; applyPause() }
            ACTION_LISTEN_NOW -> pendingListenNow = true
        }
        if (!goForeground()) return START_NOT_STICKY
        if (!started) {
            started = true
            c.scope.launch { if (c.settings.current().manualStop) c.settings.setManualStop(false) }
            begin()
        } else if (pendingListenNow && modelReady) {
            pendingListenNow = false
            listenNowInternal()
        }
        return START_STICKY
    }

    private fun goForeground(): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        ServiceCompat.startForeground(this, Notifier.ID_SERVICE, buildNotification(), type)
        true
    } catch (e: Exception) {
        // e.g. Android 14+ refuses a microphone FGS started from the background / boot.
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastBlockedPrompt > 30 * 60_000L || lastBlockedPrompt == 0L) {
            lastBlockedPrompt = now
            c.notifier.bootPrompt()
            c.log.error("النظام منع تشغيل الاستماع من الخلفية — اضغط الإشعار: ${e.message}")
        }
        stopSelf()
        false
    }

    @OptIn(FlowPreview::class)
    private fun begin() {
        _state.value = State(running = true)
        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(this, screenReceiver, screenFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val pkgFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, pkgFilter, ContextCompat.RECEIVER_EXPORTED)

        scope.launch {
            if (!c.voice.loadModel()) {
                if (pendingListenNow) {
                    pendingListenNow = false
                    c.feedback.cue()
                    c.brain.listenForCommand()
                }
                c.notifier.info("موديل الصوت مش موجود", "افتح Nabra ونزّل موديل التعرف على الصوت")
                _state.value = State(error = "الموديل مش موجود")
                stopEverything()
                return@launch
            }
            c.feedback.warmUp()
            c.log.info("المساعد بدأ يسمع")

            launch {
                c.feedback.speaking.collect { speaking = it; applyPause() }
            }
            launch {
                c.settings.flow.collect { st ->
                    directTriggers = st.directTriggers
                    voicePrint = if (st.voiceLock) com.daniel.assistant.voice.SpeakerLock.decode(st.voicePrint) else null
                    voiceThreshold = st.voiceLockThreshold
                    instantTriggers = st.instantTriggers
                    instantDelayMs = st.instantDelayMs.toLong()
                    if (st.floatingBubble) bubble.show() else bubble.hide()
                }
            }
            launch {
                combine(c.brain.listening, state) { l, st -> l || st.awaitingCommand }.collect { bubble.setActive(it) }
            }

            val settingsKey = c.settings.flow
                .map { ListenKey(it.wakeWord, it.useGrammar, it.pauseOnScreenOff, it.commandEngine == "google", it.voiceLock) }
                .distinctUntilChanged()
            combine(c.repo.commands, c.appEntriesFlow, settingsKey) { cmds, apps, s -> Triple(cmds, apps, s) }
                .debounce(500)
                .collectLatest { (cmds, apps, s) ->
                    cachedCommands = cmds
                    wakeWord = s.wakeWord
                    pauseOnScreenOff = s.pauseOnScreenOff
                    val grammar = if (s.useGrammar && c.voice.supportsGrammar()) {
                        val online = s.onlineCommands && c.google.isAvailable()
                        GrammarBuilder.toJson(
                            if (online) GrammarBuilder.wakeOnlyPhrases(s.wakeWord, cmds)
                            else GrammarBuilder.phrases(s.wakeWord, cmds, apps)
                        )
                    } else null
                    startListening(grammar)
                    modelReady = true
                    if (pendingListenNow) {
                        pendingListenNow = false
                        listenNowInternal()
                    }
                }
        }
    }

    /** Listen for one command right now (bubble, tile, shortcut, Home button). */
    private fun listenNowInternal() {
        if (c.executor.running.value != null || c.ai.running.value) return
        endWindow()
        startCommandCapture()
    }

    /** After the wake word: accurate recognizer if enabled, otherwise the offline command window. */
    private fun startCommandCapture() {
        c.feedback.cue()
        listenJob?.cancel()
        listenJob = scope.launch {
            if (!c.brain.listenForCommand()) openVoskWindow()
        }
    }

    private fun openVoskWindow() {
        _state.value = _state.value.copy(awaitingCommand = true)
        updateNotification()
        windowJob?.cancel()
        windowJob = scope.launch {
            delay(COMMAND_WINDOW_MS)
            endWindow()
        }
    }

    /** Voice lock: true when no voice print is enrolled or the last utterance matches it. */
    private fun speakerAllowed(): Boolean {
        val print = voicePrint ?: return true
        return c.speaker.matches(c.voice.lastSpeakerVector, print, voiceThreshold)
    }

    /** True when the whole utterance is (almost exactly) one of the user's triggers. */
    private fun isDirectTrigger(text: String): Boolean {
        if (!directTriggers) return false
        return cachedCommands.any { cmd ->
            cmd.enabled && cmd.triggers.any { t ->
                val n = ArabicNormalizer.normalize(t)
                n.isNotBlank() && (text == n || FuzzyMatcher.ratio(text, n) >= 0.85)
            }
        }
    }

    private suspend fun startListening(grammar: String?) {
        c.voice.startContinuous(
            VoiceEngine.Continuous(
                grammar = grammar,
                onResult = ::onResult,
                onPartial = { p ->
                    _state.value = _state.value.copy(partial = p)
                    checkPartialWake(p)
                    checkInstantTrigger(p)
                },
                onError = { e ->
                    c.log.error("خطأ في الميكروفون: ${e.message}")
                    scope.launch {
                        delay(2_000)
                        startListening(grammar)
                    }
                },
            )
        )
        _state.value = _state.value.copy(running = true, grammarMode = c.voice.grammarActive, error = null)
        applyPause()
    }

    /**
     * React to the wake word while it's still being spoken (partial result) instead of
     * waiting for the end-of-utterance silence — saves ~0.5–1 s per command.
     * Disabled with voice lock (the voice print only comes with final results).
     */
    private fun checkPartialWake(partial: String) {
        if (wakeFromPartial || voicePrint != null || _state.value.awaitingCommand) return
        if (listenJob?.isActive == true || c.executor.running.value != null || c.ai.running.value) return
        val rest = afterWakeWord(ArabicNormalizer.normalize(partial)) ?: return
        if (rest.isNotBlank()) return // wake word + command in one breath: let the final result carry it
        wakeFromPartial = true
        wakeFromPartialAt = android.os.SystemClock.uptimeMillis()
        c.brain.setHeard(partial)
        startCommandCapture()
    }

    /**
     * Instant triggers: as soon as the partial result is exactly one of the user's triggers
     * ("واحد", or "يا مساعد واحد"), run it — no waiting for the pause after the word.
     * A short stability delay avoids firing on the first word of a longer sentence.
     */
    private fun checkInstantTrigger(partial: String) {
        instantJob?.cancel()
        if (!instantTriggers || !directTriggers || voicePrint != null || _state.value.awaitingCommand) return
        if (listenJob?.isActive == true || c.executor.running.value != null || c.ai.running.value) return
        val t = ArabicNormalizer.normalize(partial)
        val core = afterWakeWord(t)?.takeIf { it.isNotBlank() } ?: t
        val key = ArabicNormalizer.phonetic(core)
        if (key.isBlank() || key == firedInstant) return
        val cmd = cachedCommands.firstOrNull { cmd ->
            cmd.enabled && cmd.triggers.any { tr ->
                !com.daniel.assistant.commands.Variables.hasVariables(tr) && ArabicNormalizer.phonetic(tr) == key
            }
        } ?: return
        instantJob = scope.launch {
            if (instantDelayMs > 0) delay(instantDelayMs)
            if (c.executor.running.value != null) return@launch
            firedInstant = key
            firedInstantAt = android.os.SystemClock.uptimeMillis()
            c.brain.runInstant(cmd, partial)
        }
    }

    private fun onResult(raw: String) {
        val text = ArabicNormalizer.normalize(raw)
        _state.value = _state.value.copy(partial = "")
        instantJob?.cancel()
        firedInstant?.let { fired ->
            val recent = android.os.SystemClock.uptimeMillis() - firedInstantAt < 4_000
            firedInstant = null
            val core = afterWakeWord(text)?.takeIf { it.isNotBlank() } ?: text
            // The final result of an instantly-fired trigger: already done.
            if (recent && ArabicNormalizer.phonetic(core).startsWith(fired)) return
        }
        c.brain.markSpeechEnd()
        if (wakeFromPartial && android.os.SystemClock.uptimeMillis() - wakeFromPartialAt > 4_000) wakeFromPartial = false
        if (wakeFromPartial) {
            wakeFromPartial = false
            val rest = afterWakeWord(text)
            if (rest.isNullOrBlank() || rest == "[unk]") return // already handled from the partial
            if (_state.value.awaitingCommand) {
                endWindow(); dispatch(rest)
            }
            return
        }
        if (text.isBlank() || text == "[unk]" && !_state.value.awaitingCommand) return

        // While a command or AI task runs, only listen for "وقف".
        if (c.executor.running.value != null || c.ai.running.value) {
            if (SysAction.CANCEL_WORDS.any { ArabicNormalizer.normalize(it) == text }) {
                c.brain.cancelAll()
                c.feedback.say("وقفت")
            }
            return
        }

        if (_state.value.awaitingCommand) {
            endWindow()
            dispatch(raw)
            return
        }

        val rest = afterWakeWord(text)
        if ((rest != null || isDirectTrigger(text)) && !speakerAllowed()) {
            c.log.info("صوت مش متعرّف عليه (قفل الصوت) — اتجاهل: \"$raw\"")
            return
        }
        if (rest == null) {
            // Custom triggers like "واحد" work even without the wake word.
            if (isDirectTrigger(text)) {
                c.brain.setHeard(raw)
                dispatch(raw)
            }
            return
        }
        c.brain.setHeard(raw)
        if (rest.isNotBlank() && rest != "[unk]") {
            dispatch(rest)
        } else {
            startCommandCapture()
        }
    }

    private fun dispatch(command: String) {
        listenJob?.cancel()
        listenJob = scope.launch { c.brain.handle(command, c.voice.grammarActive) }
    }

    private fun endWindow() {
        windowJob?.cancel()
        if (_state.value.awaitingCommand) {
            _state.value = _state.value.copy(awaitingCommand = false)
            updateNotification()
        }
    }

    /** Returns the text after the wake word (may be empty), or null if the wake word wasn't said. */
    private fun afterWakeWord(text: String): String? {
        val wake = ArabicNormalizer.normalize(wakeWord)
        if (wake.isBlank()) return null
        if (text == wake) return ""
        if (text.startsWith("$wake ")) return text.removePrefix(wake).trim()
        val words = text.split(' ')
        val n = wake.split(' ').size
        if (words.size < n) return null
        val head = words.take(n).joinToString(" ")
        return if (FuzzyMatcher.ratio(head, wake) >= 0.8) words.drop(n).joinToString(" ") else null
    }

    private fun applyPause() {
        val p = userPaused || speaking || (screenOff && pauseOnScreenOff)
        c.voice.setPaused(p)
        if (_state.value.paused != userPaused) {
            _state.value = _state.value.copy(paused = userPaused)
            updateNotification()
        }
    }

    private fun buildNotification(): Notification {
        val st = _state.value
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val toggle = PendingIntent.getService(
            this, 1,
            Intent(this, ListeningService::class.java).setAction(if (userPaused) ACTION_RESUME else ACTION_PAUSE),
            flags,
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, ListeningService::class.java).setAction(ACTION_STOP), flags,
        )
        val text = when {
            userPaused -> "متوقف مؤقتًا"
            st.awaitingCommand -> "بسمعك… قول الأمر"
            else -> "قول \"$wakeWord\" وبعدها الأمر".takeIf { wakeWord.isNotBlank() } ?: "شغال"
        }
        return NotificationCompat.Builder(this, Notifier.CH_LISTEN)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF3D7BFF.toInt())
            .setContentTitle("Nabra")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, if (userPaused) "استكمال" else "إيقاف مؤقت", toggle)
            .addAction(0, "إيقاف", stop)
            .build()
    }

    private fun updateNotification() {
        if (!started) return
        runCatching {
            androidx.core.app.NotificationManagerCompat.from(this).notify(Notifier.ID_SERVICE, buildNotification())
        }
    }

    private fun stopEverything() {
        bubble.hide()
        if (started) {
            runCatching { unregisterReceiver(screenReceiver) }
            runCatching { unregisterReceiver(packageReceiver) }
        }
        started = false
        c.scope.launch { c.voice.stopContinuous() }
        _state.value = State()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Swiping the app away from Recents must NOT stop listening. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        if (started) stopEverything()
        scope.cancel()
        super.onDestroy()
    }
}
