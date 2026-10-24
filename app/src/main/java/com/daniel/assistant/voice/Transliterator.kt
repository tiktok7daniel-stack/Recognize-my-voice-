package com.daniel.assistant.voice

/**
 * Very rough Latin → Arabic transliteration so that an English/Italian app
 * label ("Spotify") can be compared with what the Arabic model heard ("سبوتيفاي").
 */
object Transliterator {
    private val digraphs = listOf(
        "sh" to "ش", "ch" to "تش", "th" to "ث", "kh" to "خ", "gh" to "غ",
        "ph" to "ف", "oo" to "و", "ee" to "ي", "ou" to "و", "ck" to "ك", "qu" to "كو",
    )
    private val single = mapOf(
        'a' to "ا", 'b' to "ب", 'c' to "ك", 'd' to "د", 'e' to "ي", 'f' to "ف", 'g' to "ج",
        'h' to "ه", 'i' to "ي", 'j' to "ج", 'k' to "ك", 'l' to "ل", 'm' to "م", 'n' to "ن",
        'o' to "و", 'p' to "ب", 'q' to "ك", 'r' to "ر", 's' to "س", 't' to "ت", 'u' to "و",
        'v' to "ف", 'w' to "و", 'x' to "كس", 'y' to "ي", 'z' to "ز",
    )

    fun toArabic(latin: String): String {
        val words = latin.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
        return words.joinToString(" ") { word ->
            val out = StringBuilder()
            var i = 0
            while (i < word.length) {
                val pair = if (i + 1 < word.length) word.substring(i, i + 2) else ""
                val d = digraphs.firstOrNull { it.first == pair }
                if (d != null) {
                    out.append(d.second); i += 2; continue
                }
                val c = word[i]
                val mapped = single[c] ?: c.toString()
                // Drop short vowels in the middle of words (Arabic rarely writes them)
                val isVowel = c in "aeiou"
                if (!(isVowel && i > 0 && i < word.length - 1 && c in "ae")) out.append(mapped)
                i++
            }
            // collapse doubled letters
            out.toString().replace(Regex("(.)\\1+"), "$1")
        }
    }
}
