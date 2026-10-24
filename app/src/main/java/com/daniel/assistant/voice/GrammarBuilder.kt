package com.daniel.assistant.voice

import com.daniel.assistant.commands.AppEntry
import com.daniel.assistant.commands.CustomCommand
import com.daniel.assistant.commands.SysAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Builds the Vosk grammar (JSON array of phrases). Restricting the decoder to
 * these phrases gives much better accuracy and avoids false wake-ups.
 * Words missing from the model vocabulary are ignored by Vosk, so we include
 * both the original spelling (diacritics stripped) and the normalized form.
 */
object GrammarBuilder {

    fun phrases(
        wakeWord: String,
        commands: List<CustomCommand>,
        apps: List<AppEntry>,
    ): List<String> {
        val out = LinkedHashSet<String>()
        fun add(p: String) {
            val a = ArabicNormalizer.stripDiacritics(p)
            val b = ArabicNormalizer.normalize(p)
            if (a.isNotBlank()) out.add(a)
            if (b.isNotBlank()) out.add(b)
        }
        add(wakeWord)
        SysAction.entries.forEach { a ->
            a.phrases.forEach { p ->
                add(p)
                add("$wakeWord $p")
            }
        }
        SysAction.CANCEL_WORDS.forEach(::add)
        SysAction.YES_WORDS.forEach(::add)
        SysAction.NO_WORDS.forEach(::add)
        val openWords = listOf("افتح", "افتحلي", "شغل")
        for (app in apps) {
            for (name in app.spokenNames) {
                if (!ArabicNormalizer.isArabic(name)) continue
                openWords.forEach { add("$it $name") }
                // allow "<wake word> افتح X" in one breath
                add("$wakeWord افتح $name")
            }
        }
        for (c in commands) {
            if (!c.enabled) continue
            c.triggers.forEach { t ->
                add(t)
                add("$wakeWord $t")
            }
        }
        return out.toList()
    }

    fun toJson(phrases: List<String>): String =
        Json.encodeToString(JsonArray.serializer(), JsonArray((phrases + "[unk]").map { JsonPrimitive(it) }))
}
