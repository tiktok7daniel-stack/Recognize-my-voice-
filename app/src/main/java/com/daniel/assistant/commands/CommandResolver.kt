package com.daniel.assistant.commands

import com.daniel.assistant.voice.ArabicNormalizer
import com.daniel.assistant.voice.FuzzyMatcher

sealed interface Resolution {
    data class Custom(val command: CustomCommand, val score: Double, val vars: Map<String, String> = emptyMap()) : Resolution
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

        matchCustom(text, commands, heard)?.let { return it }
        matchSystem(text)?.let { return it }
        matchOpenApp(text, apps)?.let { return it }

        return if (aiAvailable) Resolution.Ai(heard) else Resolution.Unknown(heard)
    }

    fun matchCustom(text: String, commands: List<CustomCommand>, original: String = text): Resolution.Custom? {
        // Triggers with {variables} first: "ابعت لـ {اسم} {رسالة}"
        for (c in commands) {
            if (!c.enabled) continue
            for (t in c.triggers) {
                Variables.match(t, original)?.let { return Resolution.Custom(c, 1.0, it) }
            }
        }
        var best: CustomCommand? = null
        var bestScore = 0.0
        for (c in commands) {
            if (!c.enabled) continue
            for (t in c.triggers) {
                if (Variables.hasVariables(t)) continue
                val n = ArabicNormalizer.normalize(t)
                if (n.isBlank()) continue
                val s = FuzzyMatcher.phoneticRatio(text, n)
                if (s > bestScore) {
                    bestScore = s; best = c
                }
            }
        }
        // Short utterances that contain a trigger as a whole word ("يا واحد", "واحد بس")
        if ((best == null || bestScore < customThreshold) && text.split(' ').size <= 3) {
            val words = text.split(' ')
            for (c in commands) {
                if (!c.enabled) continue
                if (c.triggers.filterNot(Variables::hasVariables).any { t -> ArabicNormalizer.normalize(t).let { n -> n.isNotBlank() && !n.contains(' ') && n in words } }) {
                    return Resolution.Custom(c, 0.9)
                }
            }
        }
        return if (best != null && bestScore >= customThreshold) Resolution.Custom(best, bestScore) else null
    }

    /**
     * Resolves several recognition alternatives (e.g. Google's n-best list) and returns the
     * first one that maps to a concrete action, falling back to AI / unknown for the top one.
     */
    fun resolveBest(
        alternatives: List<String>,
        commands: List<CustomCommand>,
        apps: List<AppEntry>,
        aiAvailable: Boolean,
    ): Pair<String, Resolution> {
        val alts = alternatives.filter { it.isNotBlank() }
        if (alts.isEmpty()) return "" to Resolution.Unknown("")
        val resolved = alts.map { it to resolve(it, commands, apps, aiAvailable = false) }
        // Strongest custom match wins (earlier alternative on ties).
        resolved.filter { it.second is Resolution.Custom }
            .maxByOrNull { (it.second as Resolution.Custom).score }
            ?.let { return it }
        resolved.firstOrNull { it.second is Resolution.Cancel }?.let { return it }
        resolved.filter { it.second is Resolution.OpenApp }
            .maxByOrNull { (it.second as Resolution.OpenApp).score }
            ?.let { return it }
        resolved.firstOrNull {
            it.second !is Resolution.Unknown && it.second !is Resolution.OpenAppUnknown
        }?.let { return it }
        resolved.firstOrNull { it.second is Resolution.OpenAppUnknown }?.let { return it }
        val top = alts.first()
        return top to (if (aiAvailable) Resolution.Ai(top) else Resolution.Unknown(top))
    }

    /**
     * Fast check used on partial (still-being-spoken) results: true only when the text is
     * already an unambiguous command, so it can run before the user finishes the sentence.
     */
    fun isConfidentEarly(text: String, commands: List<CustomCommand>): Boolean {
        val t = ArabicNormalizer.normalize(text)
        if (t.isBlank()) return false
        for (c in commands) {
            if (!c.enabled) continue
            for (tr in c.triggers) {
                if (Variables.hasVariables(tr)) continue
                if (ArabicNormalizer.phonetic(tr) == ArabicNormalizer.phonetic(t)) return true
            }
        }
        return SysAction.entries.any { a -> !a.takesArg && a.phrases.any { ArabicNormalizer.phonetic(it) == ArabicNormalizer.phonetic(t) } }
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
                val s = FuzzyMatcher.phoneticRatio(text, ArabicNormalizer.normalize(p))
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
