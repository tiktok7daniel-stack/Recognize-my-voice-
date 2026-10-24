package com.daniel.assistant.voice

/**
 * Normalizes Arabic (and Latin) text so that spoken variants compare equal:
 * strips diacritics and tatweel, unifies alef/ya/ta-marbuta forms, maps
 * Arabic-Indic digits, lowercases Latin text and collapses whitespace.
 */
object ArabicNormalizer {

    private val diacritics = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")
    private val punctuation = Regex("[\\p{Punct}،؟؛«»…“”\"'`]")
    private val spaces = Regex("\\s+")

    fun normalize(input: String): String {
        if (input.isEmpty()) return input
        var s = diacritics.replace(input, "")
        val sb = StringBuilder(s.length)
        for (c in s) {
            sb.append(
                when (c) {
                    'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                    'ة' -> 'ه'
                    'ى', 'ی' -> 'ي'
                    'ؤ' -> 'و'
                    'ئ' -> 'ي'
                    'ک' -> 'ك'
                    'گ' -> 'ج'
                    'پ' -> 'ب'
                    'چ' -> 'ج'
                    'ڤ' -> 'ف'
                    in '٠'..'٩' -> '0' + (c - '٠')
                    in '۰'..'۹' -> '0' + (c - '۰')
                    else -> c.lowercaseChar()
                }
            )
        }
        s = punctuation.replace(sb.toString(), " ")
        return spaces.replace(s, " ").trim()
    }

    /** Removes diacritics only (keeps hamza forms) — used for grammar phrases. */
    fun stripDiacritics(input: String): String =
        spaces.replace(diacritics.replace(input, ""), " ").trim().lowercase()

    fun isArabic(text: String): Boolean = text.any { it in '؀'..'ۿ' }
}
