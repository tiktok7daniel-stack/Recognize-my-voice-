package com.daniel.assistant.commands

import com.daniel.assistant.voice.ArabicNormalizer
import com.daniel.assistant.voice.FuzzyMatcher

sealed interface Resolution {
    data class Custom(val command: CustomCommand, val score: Double) : Resolution
    data class System(val action: SysAction, val arg: String?) : Resolution
    /** A system command that needs free text (e.g. "اكتب") but none was heard. */
    data class NeedsArg(val action: SysAction) : Resolution
    data class OpenApp(val app: AppEntry, val score: Double) : Resolution
    data class OpenAppUnknown(val name: String) : Resolution
    data class Ai(val text: String) : Resolution
    data class Cancel(val text: String) : Resolution
    data class Unknown(val text: String) : Resolution
}

/**
 * Resolves a heard utterance with the priority:
 * custom commands → system commands → "افتح <app>" → AI (if available) → unknown.
 */
class CommandResolver(
    private val customThreshold: Double = 0.80,
    private val systemThreshold: Double = 0.85,
    private val appThreshold: Double = 0.72,
) {

    fun resolve(
        heard: String,
        commands: List<CustomCommand>,
        apps: List<AppEntry>,
        aiAvailable: Boolean,
    ): Resolution {
        val text = ArabicNormalizer.normalize(heard)
        if (text.isBlank() || text == "[unk]") return Resolution.Unknown(heard)

        if (SysAction.CANCEL_WORDS.any { ArabicNormalizer.normalize(it) == text }) {
            return Resolution.Cancel(heard)
        }

        matchCustom(text, commands)?.let { return it }
        matchSystem(text)?.let { return it }
        matchOpenApp(text, apps)?.let { return it }

        return if (aiAvailable) Resolution.Ai(heard) else Resolution.Unknown(heard)
    }

    fun matchCustom(text: String, commands: List<CustomCommand>): Resolution.Custom? {
        var best: CustomCommand? = null
        var bestScore = 0.0
        for (c in commands) {
            if (!c.enabled) continue
            for (t in c.triggers) {
                val n = ArabicNormalizer.normalize(t)
                if (n.isBlank()) continue
                val s = maxOf(
                    FuzzyMatcher.ratio(text, n),
                    FuzzyMatcher.ratio(text.replace(" ", ""), n.replace(" ", "")),
                )
                if (s > bestScore) {
                    bestScore = s; best = c
                }
            }
        }
        return if (best != null && bestScore >= customThreshold) Resolution.Custom(best, bestScore) else null
    }

    fun matchSystem(text: String): Resolution? {
        // Commands with a free-text argument: prefix match, longest phrase first.
        val argPhrases = SysAction.entries.filter { it.takesArg }
            .flatMap { a -> a.phrases.map { a to ArabicNormalizer.normalize(it) } }
            .sortedByDescending { it.second.length }
        for ((action, p) in argPhrases) {
            if (text == p) return Resolution.NeedsArg(action)
            if (text.startsWith("$p ")) {
                val arg = text.removePrefix(p).trim()
                return if (arg.isBlank() || arg == "[unk]") Resolution.NeedsArg(action) else Resolution.System(action, arg)
            }
        }

        var best: SysAction? = null
        var bestScore = 0.0
        for (a in SysAction.entries) {
            if (a.takesArg) continue
            for (p in a.phrases) {
                val s = FuzzyMatcher.ratio(text, ArabicNormalizer.normalize(p))
                if (s > bestScore) {
                    bestScore = s; best = a
                }
            }
        }
        return if (best != null && bestScore >= systemThreshold) Resolution.System(best, null) else null
    }

    fun matchOpenApp(text: String, apps: List<AppEntry>): Resolution? {
        val prefix = SysAction.OPEN_PREFIXES
            .map { ArabicNormalizer.normalize(it) }
            .sortedByDescending { it.length }
            .firstOrNull { text == it || text.startsWith("$it ") } ?: return null
        val name = text.removePrefix(prefix).trim()
        if (name.isBlank() || name == "[unk]") return Resolution.OpenAppUnknown(name)
        val match = findApp(name, apps)
        return match ?: Resolution.OpenAppUnknown(name)
    }

    fun findApp(name: String, apps: List<AppEntry>): Resolution.OpenApp? {
        val best = FuzzyMatcher.best(name, apps) { it.spokenNames } ?: return null
        return if (best.second >= appThreshold) Resolution.OpenApp(best.first, best.second) else null
    }
}
