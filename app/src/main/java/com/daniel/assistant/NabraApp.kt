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
import com.daniel.assistant.executor.CommandExecutor
import com.daniel.assistant.service.AssistantBrain
import com.daniel.assistant.service.Notifier
import com.daniel.assistant.voice.Feedback
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

    val executor = CommandExecutor(app, appIndex, feedback, notifier, log, scope, ::appEntries)
    val ai = AiAgent(app, settings, feedback, notifier, log, voice, executor, scope, ::appEntries)
    val brain = AssistantBrain(this)

    /** Installed apps merged with user aliases, as a live flow for the UI. */
    val appEntriesFlow: Flow<List<AppEntry>> = combine(appIndex.apps, repo.aliases) { _, aliases ->
        appIndex.entries(aliases.groupBy({ it.packageName }, { it.alias }))
    }

    suspend fun appEntries(): List<AppEntry> {
        appIndex.ensureLoaded()
        val aliases = repo.allAliases().groupBy({ it.packageName }, { it.alias })
        return appIndex.entries(aliases)
    }

    fun init() {
        notifier.createChannels()
        scope.launch {
            settings.flow.collect { s ->
                feedback.ttsEnabled = s.ttsEnabled
                feedback.cueMode = s.cueMode
            }
        }
        scope.launch {
            appIndex.refresh()
            DemoSeeder.seedIfNeeded(this@AppContainer)
        }
    }
}
