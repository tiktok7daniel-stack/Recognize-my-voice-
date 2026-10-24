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
    data class AttachPhotos(val count: Int, val packageName: String, val label: String) : Step()

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

    @Serializable @SerialName("system")
    data class System(val actionId: String, val arg: String? = null) : Step()

    /** Short Arabic description for lists and notifications. */
    fun describe(): String = when (this) {
        is OpenApp -> "افتح $label"
        is AttachPhotos -> "ابعت آخر $count صور لـ $label"
        is TypeText -> "اكتب \"$text\""
        PressSend -> "دوس إرسال"
        is ClickText -> "اضغط على \"$text\""
        is Wait -> "استنى $millis ms"
        Back -> "رجوع"
        Home -> "الرئيسية"
        is Scroll -> if (down) "انزل" else "اطلع"
        is System -> SysAction.byId(actionId)?.title?.let { t -> if (arg.isNullOrBlank()) t else "$t: $arg" } ?: actionId
    }

    /** Steps that need the accessibility service to work. */
    val needsAccessibility: Boolean
        get() = when (this) {
            is TypeText, PressSend, is ClickText, Back, Home, is Scroll -> true
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
    is Step.System -> StepType.SYSTEM
}

/** Domain model for a user-defined voice command. */
data class CustomCommand(
    val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val triggers: List<String>,
    val steps: List<Step>,
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
