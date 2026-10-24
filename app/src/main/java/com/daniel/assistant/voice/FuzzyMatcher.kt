package com.daniel.assistant.voice

import kotlin.math.max
import kotlin.math.min

/** Similarity scoring on normalized text (0.0 – 1.0). */
object FuzzyMatcher {

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    fun ratio(a: String, b: String): Double {
        val len = max(a.length, b.length)
        if (len == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / len
    }

    /**
     * Combined score: best of plain ratio, space-insensitive ratio and a
     * containment bonus (one phrase fully contains the other).
     */
    fun score(heardRaw: String, candidateRaw: String): Double {
        val a = ArabicNormalizer.normalize(heardRaw)
        val b = ArabicNormalizer.normalize(candidateRaw)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val r1 = ratio(a, b)
        val r2 = ratio(a.replace(" ", ""), b.replace(" ", ""))
        val shorter = if (a.length <= b.length) a else b
        val longer = if (a.length <= b.length) b else a
        val contain = if (shorter.length >= 3 && containsWord(longer, shorter)) 0.9 else 0.0
        return maxOf(r1, r2, contain)
    }

    private fun containsWord(longer: String, shorter: String): Boolean {
        val idx = longer.indexOf(shorter)
        if (idx < 0) return false
        val before = idx == 0 || longer[idx - 1] == ' '
        val end = idx + shorter.length
        val after = end == longer.length || longer[end] == ' '
        return before && after
    }

    fun <T> best(heard: String, items: Iterable<T>, names: (T) -> Iterable<String>): Pair<T, Double>? {
        var bestItem: T? = null
        var bestScore = 0.0
        for (item in items) {
            for (n in names(item)) {
                val s = score(heard, n)
                if (s > bestScore) {
                    bestScore = s; bestItem = item
                }
            }
        }
        return bestItem?.let { it to bestScore }
    }
}
