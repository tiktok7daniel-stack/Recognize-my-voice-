package com.daniel.assistant.commands

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One action in a custom command chain. Serialized with a "type" discriminator. */
@Serializable
sealed class Step {
    @Serializable @SerialName("open_app")
    data class OpenApp(val packageName: String, val label: String) : Step()

    @Serializable @SerialName("attach_photos")
    data class AttachPhotos(
        val count: Int,
        val packageName: String,
        val label: String,
        /** Optional text shared together with the photos (pre-fills the message in many apps). */
        val text: String? = null,
        /** Send into this existing chat (link opened inside the app) instead of a new chat. */
        val chatUrl: String? = null,
    ) : Step()

    @Serializable @SerialName("type_text")
    data class TypeText(val text: String) : Step()

    @Serializable @SerialName("press_send")
    data object PressSend : Step()

    @Serializable @SerialName("click_text")
    data class ClickText(val text: String) : Step()

    @Serializable @SerialName("wait")
    data class Wait(val millis: Long) : Step()

    @Serializable @SerialName("back")
    data object Back : Step()

    @Serializable @SerialName("home")
    data object Home : Step()

    @Serializable @SerialName("scroll")
    data class Scroll(val down: Boolean = true) : Step()

    @Serializable @SerialName("open_url")
    data class OpenUrl(
        val url: String,
        /** Open inside this app (e.g. a Claude chat link inside the Claude app), or null to auto-detect. */
        val packageName: String? = null,
    ) : Step()

    /** Attach the newest photos from inside the chat that is already open (no new chat). */
    @Serializable @SerialName("attach_in_chat")
    data class AttachInChat(val count: Int = 2) : Step()

    @Serializable @SerialName("system")
    data class System(val actionId: String, val arg: String? = null) : Step()

    /** Short Arabic description for lists and notifications. */
    fun describe(): String = when (this) {
        is OpenApp -> "افتح $label"
        is AttachPhotos -> if (!chatUrl.isNullOrBlank()) "ابعت آخر $count صور لشات معيّن في $label"
            else "ابعت آخر $count صور لـ $label" + (text?.takeIf { it.isNotBlank() }?.let { " + \"$it\"" } ?: "")
        is TypeText -> "اكتب \"$text\""
        PressSend -> "دوس إرسال"
        is ClickText -> "اضغط على \"$text\""
        is Wait -> "استنى $millis ms"
        Back -> "رجوع"
        Home -> "الرئيسية"
        is Scroll -> if (down) "انزل" else "اطلع"
        is OpenUrl -> if (ChatLinks.isClaudeChat(url)) "افتح الشات في Claude" else "افتح $url"
        is AttachInChat -> "ارفق آخر $count صور جوه الشات"
        is System -> SysAction.byId(actionId)?.title?.let { t -> if (arg.isNullOrBlank()) t else "$t: $arg" } ?: actionId
    }

    /** Steps that need the accessibility service to work. */
    val needsAccessibility: Boolean
        get() = when (this) {
            is TypeText, PressSend, is ClickText, Back, Home, is Scroll, is AttachInChat -> true
            is AttachPhotos -> !chatUrl.isNullOrBlank()
            is System -> SysAction.byId(actionId)?.needsAccessibility ?: false
            else -> false
        }
}

enum class StepType(val title: String) {
    OPEN_APP("افتح تطبيق"),
    ATTACH_PHOTOS("ابعت آخر صور لتطبيق"),
    TYPE_TEXT("اكتب نص"),
    PRESS_SEND("دوس إرسال"),
    CLICK_TEXT("اضغط على نص"),
    WAIT("استنى"),
    BACK("رجوع"),
    HOME("الرئيسية"),
    SCROLL("سكرول"),
    OPEN_URL("افتح لينك / شات معيّن"),
    ATTACH_IN_CHAT("ارفق صور جوه الشات المفتوح"),
    SYSTEM("أمر نظام"),
}

fun Step.type(): StepType = when (this) {
    is Step.OpenApp -> StepType.OPEN_APP
    is Step.AttachPhotos -> StepType.ATTACH_PHOTOS
    is Step.TypeText -> StepType.TYPE_TEXT
    Step.PressSend -> StepType.PRESS_SEND
    is Step.ClickText -> StepType.CLICK_TEXT
    is Step.Wait -> StepType.WAIT
    Step.Back -> StepType.BACK
    Step.Home -> StepType.HOME
    is Step.Scroll -> StepType.SCROLL
    is Step.OpenUrl -> StepType.OPEN_URL
    is Step.AttachInChat -> StepType.ATTACH_IN_CHAT
    is Step.System -> StepType.SYSTEM
}

/** Domain model for a user-defined voice command. */
data class CustomCommand(
    val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val triggers: List<String>,
    val steps: List<Step>,
    /** Run automatically every day at this time ("HH:mm"), or null. */
    val autoTime: String? = null,
    /** Run automatically when this app (package) comes to the foreground, or null. */
    val autoApp: String? = null,
    /** Run when this physical-button gesture happens (ButtonGesture id), or null. */
    val autoButton: String? = null,
)

object CommandJson {
    val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        encodeDefaults = true
        prettyPrint = false
    }
    val pretty = Json(json) { prettyPrint = true }
}

/** Helpers for sending to an existing chat instead of a new one. */
object ChatLinks {
    private val claudeChat = Regex("""https?://(www\.)?claude\.ai/chat/[0-9a-fA-F-]{8,}""")

    fun isClaudeChat(url: String) = claudeChat.containsMatchIn(url.trim())
    fun isShareLink(url: String) = url.contains("claude.ai/share/")

    /** Best app to open a link inside (only well-known chat apps). */
    fun packageFor(url: String): String? {
        val u = url.lowercase()
        return when {
            u.contains("claude.ai/") -> "com.anthropic.claude"
            u.contains("chatgpt.com/") || u.contains("chat.openai.com/") -> "com.openai.chatgpt"
            u.contains("gemini.google.com/") -> "com.google.android.apps.bard"
            u.contains("wa.me/") || u.contains("whatsapp.com/") -> "com.whatsapp"
            u.contains("t.me/") -> "org.telegram.messenger"
            else -> null
        }
    }

    /**
     * Turns "share photos to app" (which always starts a NEW chat) into
     * "open this existing chat → attach photos inside it". Keeps every other step.
     */
    fun sendToExistingChat(steps: List<Step>, url: String): List<Step> {
        val i = steps.indexOfFirst { it is Step.AttachPhotos }
        if (i < 0) return steps
        val share = steps[i] as Step.AttachPhotos
        val before = steps.take(i).filterNot { it is Step.OpenApp && it.packageName == share.packageName }
        val replacement = listOf(
            Step.OpenUrl(url.trim(), packageFor(url) ?: share.packageName.ifBlank { null }),
            Step.AttachInChat(share.count),
        )
        return before + replacement + steps.drop(i + 1)
    }
}
