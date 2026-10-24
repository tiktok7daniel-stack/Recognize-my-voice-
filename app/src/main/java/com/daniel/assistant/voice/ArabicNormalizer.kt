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

    private val buckwalter = mapOf(
        '\'' to "ء", '|' to "آ", '>' to "أ", '&' to "ؤ", '<' to "إ", '}' to "ئ", 'A' to "ا", 'b' to "ب",
        'p' to "ة", 't' to "ت", 'v' to "ث", 'j' to "ج", 'H' to "ح", 'x' to "خ", 'd' to "د", '*' to "ذ",
        'r' to "ر", 'z' to "ز", 's' to "س", '$' to "ش", 'S' to "ص", 'D' to "ض", 'T' to "ط", 'Z' to "ظ",
        'E' to "ع", 'g' to "غ", 'f' to "ف", 'q' to "ق", 'k' to "ك", 'l' to "ل", 'm' to "م", 'n' to "ن",
        'h' to "ه", 'w' to "و", 'Y' to "ى", 'y' to "ي", '{' to "ا",
        // short vowels / shadda / sukun are dropped
        'F' to "", 'N' to "", 'K' to "", 'a' to "", 'u' to "", 'i' to "", '~' to "", 'o' to "", '`' to "", '_' to "",
    )
    private val buckwalterSpecial = Regex("[|><&}*$'{~`]|[A-Z]")

    /**
     * Some Kaldi Arabic models output Buckwalter transliteration ("wAHd") instead of Arabic
     * script. Converts such output to Arabic so it can be matched; other text is returned as is.
     */
    fun fromBuckwalterIfNeeded(text: String): String {
        if (text.isBlank() || isArabic(text) || text.contains("[unk]")) return text
        if (!buckwalterSpecial.containsMatchIn(text)) return text
        if (text.any { it !in buckwalter && it != ' ' && !it.isDigit() }) return text
        return text.split(' ').joinToString(" ") { w -> w.map { buckwalter[it] ?: it.toString() }.joinToString("") }
    }
}
