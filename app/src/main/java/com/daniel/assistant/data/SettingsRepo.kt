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
    val startOnBoot: Boolean = false,
    val pauseOnScreenOff: Boolean = true,
    val useGrammar: Boolean = true,
    val threshold: Float = 0.80f,
    val modelUrl: String = VoskModels.DEFAULT.url,
    val demoSeeded: Boolean = false,
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
