package com.daniel.assistant.commands

import com.daniel.assistant.voice.ArabicNormalizer

/**
 * Triggers with variables: "ابعت لـ {اسم} {رسالة}" matches "ابعت لأحمد أنا جاي" and gives
 * {اسم}=أحمد, {رسالة}=أنا جاي. Values keep the original spelling; steps use them as {اسم}.
 */
object Variables {
    private val placeholder = Regex("\\{([^{}]+)\\}")

    fun hasVariables(trigger: String) = placeholder.containsMatchIn(trigger)

    fun names(trigger: String): List<String> = placeholder.findAll(trigger).map { it.groupValues[1].trim() }.toList()

    /** Trigger text without placeholders (for grammar / biasing). */
    fun literal(trigger: String): String = placeholder.replace(trigger, " ").replace(Regex("\\s+"), " ").trim()

    private fun charClass(c: Char): String = when (c) {
        'ا' -> "[اأإآٱ]"
        'ه' -> "[هة]"
        'ي' -> "[يىئ]"
        'و' -> "[وؤ]"
        else -> Regex.escape(c.toString())
    }

    private fun literalPattern(part: String): String {
        val n = ArabicNormalizer.normalize(part)
        if (n.isEmpty()) return ""
        return n.split(' ').joinToString("\\s+") { w -> w.map(::charClass).joinToString("") }
    }

    /** Returns variable values if [heard] matches [trigger], else null. */
    fun match(trigger: String, heard: String): Map<String, String>? {
        if (!hasVariables(trigger)) return null
        val names = names(trigger)
        val literals = placeholder.split(trigger)
        val sb = StringBuilder("^\\s*")
        literals.forEachIndexed { i, lit ->
            val p = literalPattern(lit)
            if (p.isNotEmpty()) sb.append(p)
            if (i < names.size) {
                sb.append("\\s*")
                sb.append(if (i == names.lastIndex) "(.+)" else "(.+?)")
                sb.append(if (i == names.lastIndex && literals.getOrNull(i + 1).isNullOrBlank()) "" else "\\s+")
            }
        }
        sb.append("\\s*$")
        val text = ArabicNormalizer.stripDiacritics(heard).replace('ـ', ' ').replace(Regex("\\s+"), " ").trim()
        val m = Regex(sb.toString(), RegexOption.IGNORE_CASE).find(text) ?: return null
        return names.mapIndexed { i, name -> name to m.groupValues[i + 1].trim() }
            .takeIf { pairs -> pairs.all { it.second.isNotBlank() } }
            ?.toMap()
    }

    fun fill(text: String, vars: Map<String, String>): String =
        if (vars.isEmpty()) text else placeholder.replace(text) { vars[it.groupValues[1].trim()] ?: it.value }
}

/** Substitutes {variables} inside a step's text parameters. */
fun Step.withVars(vars: Map<String, String>): Step = if (vars.isEmpty()) this else when (this) {
    is Step.TypeText -> copy(text = Variables.fill(text, vars))
    is Step.ClickText -> copy(text = Variables.fill(text, vars))
    is Step.OpenApp -> copy(label = Variables.fill(label, vars))
    is Step.AttachPhotos -> copy(text = text?.let { Variables.fill(it, vars) })
    is Step.System -> copy(arg = arg?.let { Variables.fill(it, vars) })
    is Step.OpenUrl -> copy(url = Variables.fill(url, vars))
    is Step.AttachInChat -> this
    else -> this
}
