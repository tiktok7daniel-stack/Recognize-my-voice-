package com.daniel.assistant

import android.app.Application
import com.daniel.assistant.ai.AiAgent
import com.daniel.assistant.commands.AppEntry
import com.daniel.assistant.commands.DemoSeeder
import com.daniel.assistant.data.AppIndex
import com.daniel.assistant.data.CommandRepository
import com.daniel.assistant.data.ExecLog
import com.daniel.assistant.data.SettingsRepo
import com.daniel.assistant.data.db.AppDatabase
import com.daniel.assistant.accessibility.AssistantAccessibilityService
import com.daniel.assistant.accessibility.ButtonGesture
import com.daniel.assistant.accessibility.ButtonGestures
import com.daniel.assistant.executor.Automations
import com.daniel.assistant.executor.CommandExecutor
import com.daniel.assistant.service.AssistantBrain
import com.daniel.assistant.service.ExecutionOverlay
import com.daniel.assistant.service.Notifier
import com.daniel.assistant.voice.Feedback
import com.daniel.assistant.voice.GoogleRecognizer
import com.daniel.assistant.voice.SpeakerLock
import com.daniel.assistant.voice.ModelManager
import com.daniel.assistant.voice.VoiceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class NabraApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.init()
    }
}

/** Hand-written dependency container shared by the UI and the service. */
class AppContainer(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val db = AppDatabase.create(app)
    val settings = SettingsRepo(app)
    val repo = CommandRepository(db.commands(), db.aliases())
    val log = ExecLog(db.logs(), scope)
    val appIndex = AppIndex(app)
    val notifier = Notifier(app)
    val feedback = Feedback(app)
    val models = ModelManager(app, scope)
    val voice = VoiceEngine(models)
    val google = GoogleRecognizer(app)
    val speaker = SpeakerLock(app)

    val executor = CommandExecutor(app, appIndex, feedback, notifier, log, scope, ::appEntries)
    val ai = AiAgent(app, settings, feedback, notifier, log, voice, executor, scope, ::appEntries)
    val brain = AssistantBrain(this)
    val automations = Automations(app)
    val skills = com.daniel.assistant.skills.SkillRunner(app, this)
    val telegram = com.daniel.assistant.service.TelegramRemote(this)
    private val overlay = ExecutionOverlay(app) { brain.cancelAll(); feedback.say("وقفت") }

    /** Installed apps merged with user aliases, as a live flow for the UI. */
    val appEntriesFlow: Flow<List<AppEntry>> = combine(appIndex.apps, repo.aliases) { _, aliases ->
        appIndex.entries(aliases.groupBy({ it.packageName }, { it.alias }))
    }

    /** Listen for a command now — via the service, or through the activity if a background start is blocked. */
    fun startListenNow() {
        try {
            com.daniel.assistant.service.ListeningService.listenNow(app)
        } catch (e: Exception) {
            com.daniel.assistant.executor.AppLauncher.start(
                app,
                android.content.Intent(app, com.daniel.assistant.ui.MainActivity::class.java)
                    .setAction(com.daniel.assistant.service.ListeningService.ACTION_LISTEN_NOW),
            )
        }
    }

    suspend fun appEntries(): List<AppEntry> {
        appIndex.ensureLoaded()
        val aliases = repo.allAliases().groupBy({ it.packageName }, { it.alias })
        return appIndex.entries(aliases)
    }

    fun init() {
        notifier.createChannels()
        brain.attachCaches(scope)
        telegram.attach(scope)
        google.warmUp()
        scope.launch {
            settings.flow.collect { s ->
                feedback.ttsEnabled = s.ttsEnabled
                feedback.cueMode = s.cueMode
                // Voice lock needs the speaker model attached to every recognizer.
                voice.speakerModel = if (s.voiceLock && speaker.isDownloaded()) speaker.load() else null
            }
        }
        scope.launch {
            combine(executor.progress, ai.running, ai.status, ai.step) { p, aiRun, aiStatus, aiStep ->
                when {
                    p != null -> Triple("▶ ${p.command}", "خطوة ${p.index} من ${p.total}: ${p.step}", p.index to p.total)
                    aiRun -> Triple("🤖 الذكاء الاصطناعي", "خطوة $aiStep: ${aiStatus ?: "…"}", aiStep to AiAgent.MAX_STEPS)
                    else -> null
                }
            }.collect { info ->
                if (info == null) overlay.hide()
                else overlay.show(info.first, info.second, info.third.first, info.third.second)
            }
        }
        scope.launch {
            repo.commands.collect { automations.schedule(it) }
        }
        // Physical buttons: keep the detector's active gestures in sync and react to them.
        scope.launch {
            combine(repo.commands, settings.flow) { cmds, s ->
                (cmds.filter { it.enabled }.mapNotNull { ButtonGesture.byId(it.autoButton) } +
                    listOfNotNull(ButtonGesture.byId(s.listenButton))).toSet()
            }.collect { ButtonGestures.active = it }
        }
        scope.launch {
            ButtonGestures.events.collect { g ->
                feedback.cue()
                if (settings.current().listenButton == g.id) {
                    startListenNow()
                    return@collect
                }
                repo.all().firstOrNull { it.enabled && it.autoButton == g.id }?.let { cmd ->
                    log.info("زرار (${g.title}) ← \"${cmd.name}\"")
                    executor.run(cmd)
                }
            }
        }
        scope.launch {
            AssistantAccessibilityService.appOpened.collect { pkg ->
                if (executor.running.value != null || ai.running.value) return@collect
                automations.commandForApp(pkg, repo.all())?.let { cmd ->
                    log.info("تشغيل تلقائي: \"${cmd.name}\" عند فتح ${appIndex.labelOf(pkg) ?: pkg}")
                    executor.run(cmd, announce = false)
                }
            }
        }
        scope.launch {
            appIndex.refresh()
            DemoSeeder.seedIfNeeded(this@AppContainer)
        }
    }
}
