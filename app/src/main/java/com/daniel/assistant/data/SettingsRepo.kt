package com.daniel.assistant.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.daniel.assistant.ai.AiDefaults
import com.daniel.assistant.voice.VoskModels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class CueMode(val id: String, val title: String) {
    VIBRATE("vibrate", "اهتزاز"), BEEP("beep", "صوت"), BOTH("both", "الاتنين");

    companion object { fun byId(id: String?) = entries.firstOrNull { it.id == id } ?: BOTH }
}

data class AppSettings(
    val onboardingDone: Boolean = false,
    val tutorialDone: Boolean = false,
    val editorHintsSeen: Boolean = false,
    val wakeWord: String = "يا مساعد",
    val ttsEnabled: Boolean = true,
    val cueMode: CueMode = CueMode.BOTH,
    val startOnBoot: Boolean = true,
    val pauseOnScreenOff: Boolean = true,
    val useGrammar: Boolean = true,
    val threshold: Float = 0.80f,
    val modelUrl: String = VoskModels.DEFAULT.url,
    val demoSeeded: Boolean = false,
    val demoTextV2: Boolean = false,
    val demoV3: Boolean = false,
    val demoV4: Boolean = false,
    val demoV5: Boolean = false,
    val demoV6: Boolean = false,
    /** Run direct triggers the moment the word is recognized (partial result), not after the pause. */
    val instantTriggers: Boolean = true,
    /** Start listening automatically (app open, accessibility watchdog, boot). */
    val autoStart: Boolean = true,
    /** The user pressed Stop: don't auto-restart until they open the app again. */
    val manualStop: Boolean = false,
    val telegramEnabled: Boolean = false,
    val telegramTokenEnc: String = "",
    val telegramChatId: String = "",
    val telegramPairCode: String = "",
    /** How long the recognized trigger must stay unchanged before firing (0 = immediately). */
    val instantDelayMs: Int = 150,
    /** "google" (Android speech, most accurate for Egyptian Arabic) or "vosk" (fully offline). */
    val commandEngine: String = "google",
    /** Run custom command triggers even without the wake word. */
    val directTriggers: Boolean = true,
    val floatingBubble: Boolean = false,
    val voiceLock: Boolean = false,
    /** Button gesture that starts listening for a command (ButtonGesture id), or "". */
    val listenButton: String = "",
    val voicePrint: String = "",
    val voiceLockThreshold: Float = 0.55f,
    val aiEnabled: Boolean = false,
    val aiProvider: String = "anthropic",
    val aiModel: String = AiDefaults.ANTHROPIC_MODEL,
    val aiBaseUrl: String = AiDefaults.OPENAI_BASE_URL,
    val aiKeyEncrypted: String = "",
    val aiConfirmSensitive: Boolean = true,
)

class SettingsRepo(private val context: Context) {

    private object K {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val tutorialDone = booleanPreferencesKey("tutorial_done")
        val editorHints = booleanPreferencesKey("editor_hints_seen")
        val wakeWord = stringPreferencesKey("wake_word")
        val tts = booleanPreferencesKey("tts")
        val cue = stringPreferencesKey("cue")
        val boot = booleanPreferencesKey("boot")
        val screenOff = booleanPreferencesKey("pause_screen_off")
        val grammar = booleanPreferencesKey("grammar")
        val threshold = floatPreferencesKey("threshold")
        val modelUrl = stringPreferencesKey("model_url")
        val demoSeeded = booleanPreferencesKey("demo_seeded")
        val demoTextV2 = booleanPreferencesKey("demo_text_v2")
        val demoV3 = booleanPreferencesKey("demo_v3")
        val demoV4 = booleanPreferencesKey("demo_v4")
        val demoV5 = booleanPreferencesKey("demo_v5")
        val demoV6 = booleanPreferencesKey("demo_v6")
        val instant = booleanPreferencesKey("instant_triggers")
        val autoStart = booleanPreferencesKey("auto_start")
        val manualStop = booleanPreferencesKey("manual_stop")
        val tgEnabled = booleanPreferencesKey("tg_enabled")
        val tgToken = stringPreferencesKey("tg_token_enc")
        val tgChat = stringPreferencesKey("tg_chat")
        val tgCode = stringPreferencesKey("tg_code")
        val instantDelay = androidx.datastore.preferences.core.intPreferencesKey("instant_delay_ms")
        val engine = stringPreferencesKey("command_engine")
        val direct = booleanPreferencesKey("direct_triggers")
        val bubble = booleanPreferencesKey("floating_bubble")
        val voiceLock = booleanPreferencesKey("voice_lock")
        val listenButton = stringPreferencesKey("listen_button")
        val voicePrint = stringPreferencesKey("voice_print")
        val voiceLockThreshold = floatPreferencesKey("voice_lock_threshold")
        val aiEnabled = booleanPreferencesKey("ai_enabled")
        val aiProvider = stringPreferencesKey("ai_provider")
        val aiModel = stringPreferencesKey("ai_model")
        val aiBaseUrl = stringPreferencesKey("ai_base_url")
        val aiKey = stringPreferencesKey("ai_key_enc")
        val aiConfirm = booleanPreferencesKey("ai_confirm")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            onboardingDone = p[K.onboardingDone] ?: d.onboardingDone,
            tutorialDone = p[K.tutorialDone] ?: d.tutorialDone,
            editorHintsSeen = p[K.editorHints] ?: d.editorHintsSeen,
            wakeWord = p[K.wakeWord]?.takeIf { it.isNotBlank() } ?: d.wakeWord,
            ttsEnabled = p[K.tts] ?: d.ttsEnabled,
            cueMode = CueMode.byId(p[K.cue]),
            startOnBoot = p[K.boot] ?: d.startOnBoot,
            pauseOnScreenOff = p[K.screenOff] ?: d.pauseOnScreenOff,
            useGrammar = p[K.grammar] ?: d.useGrammar,
            threshold = p[K.threshold] ?: d.threshold,
            modelUrl = p[K.modelUrl]?.takeIf { it.isNotBlank() } ?: d.modelUrl,
            demoSeeded = p[K.demoSeeded] ?: d.demoSeeded,
            demoTextV2 = p[K.demoTextV2] ?: d.demoTextV2,
            demoV3 = p[K.demoV3] ?: d.demoV3,
            demoV4 = p[K.demoV4] ?: d.demoV4,
            demoV5 = p[K.demoV5] ?: d.demoV5,
            demoV6 = p[K.demoV6] ?: d.demoV6,
            instantTriggers = p[K.instant] ?: d.instantTriggers,
            autoStart = p[K.autoStart] ?: d.autoStart,
            manualStop = p[K.manualStop] ?: d.manualStop,
            telegramEnabled = p[K.tgEnabled] ?: d.telegramEnabled,
            telegramTokenEnc = p[K.tgToken] ?: d.telegramTokenEnc,
            telegramChatId = p[K.tgChat] ?: d.telegramChatId,
            telegramPairCode = p[K.tgCode] ?: d.telegramPairCode,
            instantDelayMs = p[K.instantDelay] ?: d.instantDelayMs,
            commandEngine = p[K.engine] ?: d.commandEngine,
            directTriggers = p[K.direct] ?: d.directTriggers,
            floatingBubble = p[K.bubble] ?: d.floatingBubble,
            voiceLock = p[K.voiceLock] ?: d.voiceLock,
            listenButton = p[K.listenButton] ?: d.listenButton,
            voicePrint = p[K.voicePrint] ?: d.voicePrint,
            voiceLockThreshold = p[K.voiceLockThreshold] ?: d.voiceLockThreshold,
            aiEnabled = p[K.aiEnabled] ?: d.aiEnabled,
            aiProvider = p[K.aiProvider] ?: d.aiProvider,
            aiModel = p[K.aiModel]?.takeIf { it.isNotBlank() } ?: d.aiModel,
            aiBaseUrl = p[K.aiBaseUrl]?.takeIf { it.isNotBlank() } ?: d.aiBaseUrl,
            aiKeyEncrypted = p[K.aiKey] ?: d.aiKeyEncrypted,
            aiConfirmSensitive = p[K.aiConfirm] ?: d.aiConfirmSensitive,
        )
    }

    suspend fun current(): AppSettings = flow.first()

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    suspend fun setOnboardingDone(v: Boolean) = edit { it[K.onboardingDone] = v }
    suspend fun setTutorialDone(v: Boolean) = edit { it[K.tutorialDone] = v }
    suspend fun setEditorHintsSeen(v: Boolean) = edit { it[K.editorHints] = v }
    suspend fun setWakeWord(v: String) = edit { it[K.wakeWord] = v.trim() }
    suspend fun setTts(v: Boolean) = edit { it[K.tts] = v }
    suspend fun setCue(v: CueMode) = edit { it[K.cue] = v.id }
    suspend fun setStartOnBoot(v: Boolean) = edit { it[K.boot] = v }
    suspend fun setPauseOnScreenOff(v: Boolean) = edit { it[K.screenOff] = v }
    suspend fun setUseGrammar(v: Boolean) = edit { it[K.grammar] = v }
    suspend fun setThreshold(v: Float) = edit { it[K.threshold] = v }
    suspend fun setModelUrl(v: String) = edit { it[K.modelUrl] = v.trim() }
    suspend fun setDemoSeeded(v: Boolean) = edit { it[K.demoSeeded] = v }
    suspend fun setDemoTextV2(v: Boolean) = edit { it[K.demoTextV2] = v }
    suspend fun setDemoV3(v: Boolean) = edit { it[K.demoV3] = v }
    suspend fun setDemoV4(v: Boolean) = edit { it[K.demoV4] = v }
    suspend fun setDemoV5(v: Boolean) = edit { it[K.demoV5] = v }
    suspend fun setDemoV6(v: Boolean) = edit { it[K.demoV6] = v }
    suspend fun setInstantTriggers(v: Boolean) = edit { it[K.instant] = v }
    suspend fun setAutoStart(v: Boolean) = edit { it[K.autoStart] = v }
    suspend fun setManualStop(v: Boolean) = edit { it[K.manualStop] = v }
    suspend fun setTelegramEnabled(v: Boolean) = edit { it[K.tgEnabled] = v }
    suspend fun setTelegramChatId(v: String) = edit { it[K.tgChat] = v }
    suspend fun setTelegramToken(plain: String) = edit {
        it[K.tgToken] = if (plain.isBlank()) "" else SecureKeyStore.encrypt(plain.trim())
        it[K.tgChat] = ""
        it[K.tgCode] = (100000..999999).random().toString()
    }
    suspend fun telegramToken(): String {
        val enc = current().telegramTokenEnc
        return if (enc.isBlank()) "" else runCatching { SecureKeyStore.decrypt(enc) }.getOrDefault("")
    }
    suspend fun setInstantDelay(v: Int) = edit { it[K.instantDelay] = v }
    suspend fun setCommandEngine(v: String) = edit { it[K.engine] = v }
    suspend fun setDirectTriggers(v: Boolean) = edit { it[K.direct] = v }
    suspend fun setFloatingBubble(v: Boolean) = edit { it[K.bubble] = v }
    suspend fun setVoiceLock(v: Boolean) = edit { it[K.voiceLock] = v }
    suspend fun setListenButton(v: String) = edit { it[K.listenButton] = v }
    suspend fun setVoicePrint(v: String) = edit { it[K.voicePrint] = v }
    suspend fun setVoiceLockThreshold(v: Float) = edit { it[K.voiceLockThreshold] = v }
    suspend fun setAiEnabled(v: Boolean) = edit { it[K.aiEnabled] = v }
    suspend fun setAiProvider(v: String) = edit { it[K.aiProvider] = v }
    suspend fun setAiModel(v: String) = edit { it[K.aiModel] = v.trim() }
    suspend fun setAiBaseUrl(v: String) = edit { it[K.aiBaseUrl] = v.trim() }
    suspend fun setAiConfirm(v: Boolean) = edit { it[K.aiConfirm] = v }

    suspend fun setApiKey(plain: String) = edit {
        it[K.aiKey] = if (plain.isBlank()) "" else SecureKeyStore.encrypt(plain)
    }

    suspend fun apiKey(): String {
        val enc = current().aiKeyEncrypted
        return if (enc.isBlank()) "" else runCatching { SecureKeyStore.decrypt(enc) }.getOrDefault("")
    }
}
