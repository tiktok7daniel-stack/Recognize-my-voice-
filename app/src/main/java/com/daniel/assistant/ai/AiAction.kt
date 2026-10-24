package com.daniel.assistant.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** One action chosen by the model for the current screen. */
sealed interface AiAction {
    val sensitive: Boolean

    data class Click(val index: Int, override val sensitive: Boolean = false) : AiAction
    data class Type(val index: Int, val text: String, override val sensitive: Boolean = false) : AiAction
    data class OpenApp(val name: String, override val sensitive: Boolean = false) : AiAction
    data class Scroll(val down: Boolean, override val sensitive: Boolean = false) : AiAction
    data class Back(override val sensitive: Boolean = false) : AiAction
    data class Home(override val sensitive: Boolean = false) : AiAction
    data class Wait(val millis: Long, override val sensitive: Boolean = false) : AiAction
    data class Done(val say: String, override val sensitive: Boolean = false) : AiAction

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** Extracts the first JSON object from the model reply and parses it. */
        fun parse(reply: String): AiAction? {
            val start = reply.indexOf('{')
            if (start < 0) return null
            var depth = 0
            var end = -1
            var inString = false
            var escape = false
            for (i in start until reply.length) {
                val c = reply[i]
                if (inString) {
                    if (escape) escape = false
                    else if (c == '\\') escape = true
                    else if (c == '"') inString = false
                    continue
                }
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) { end = i; break }
                    }
                }
            }
            if (end < 0) return null
            val obj = runCatching { json.parseToJsonElement(reply.substring(start, end + 1)) as? JsonObject }
                .getOrNull() ?: return null
            fun str(k: String) = obj[k]?.jsonPrimitive?.content
            fun int(k: String) = obj[k]?.jsonPrimitive?.intOrNull
            val sensitive = obj["sensitive"]?.jsonPrimitive?.booleanOrNull ?: false
            return when (str("action")?.lowercase()) {
                "click", "tap" -> int("index")?.let { Click(it, sensitive) }
                "type" -> {
                    val idx = int("index") ?: -1
                    Type(idx, str("text") ?: "", sensitive)
                }
                "open_app", "open" -> str("name")?.let { OpenApp(it, sensitive) }
                "scroll" -> Scroll(str("direction")?.lowercase() != "up", sensitive)
                "back" -> Back(sensitive)
                "home" -> Home(sensitive)
                "wait" -> Wait(obj["ms"]?.jsonPrimitive?.longOrNull ?: 1000L, sensitive)
                "done", "finish" -> Done(str("say") ?: "", sensitive)
                else -> null
            }
        }
    }
}

object AiPrompts {
    val SYSTEM = """
You control an Android phone for the user through its accessibility tree.
The user speaks Egyptian Arabic. Each turn you get the user's command, the actions
already taken, and the CURRENT screen as numbered lines:
index|class|text|description|flags|[left,top,right,bottom]
flags: C=clickable, E=editable, S=scrollable, F=focused.

Reply with exactly ONE action as a single JSON object and nothing else:
{"action":"click","index":5}
{"action":"type","index":3,"text":"..."}
{"action":"open_app","name":"YouTube"}
{"action":"scroll","direction":"down"}   (or "up")
{"action":"back"}
{"action":"home"}
{"action":"wait","ms":1000}
{"action":"done","say":"short Egyptian Arabic confirmation"}

Rules:
- Add "sensitive":true to any action that sends a message, posts, buys, pays,
  transfers money, deletes anything, or changes system settings.
- Use only indexes that appear on the current screen.
- If the task is complete or impossible, reply with "done" and explain briefly in Egyptian Arabic.
- Never interact with banking, payment or password-manager apps.
""".trimIndent()

    fun userTurn(command: String, history: List<String>, screen: String): String = buildString {
        append("Command: ").append(command).append('\n')
        if (history.isNotEmpty()) {
            append("Actions so far:\n")
            history.forEachIndexed { i, h -> append(i + 1).append(". ").append(h).append('\n') }
        }
        append("Current screen:\n").append(screen)
    }
}
