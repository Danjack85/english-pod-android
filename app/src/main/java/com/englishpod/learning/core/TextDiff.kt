package com.englishpod.learning.core

/** Word level comparison used by the dictation drills. */
object TextDiff {

    enum class Kind { MATCH, MISSING, EXTRA }

    data class Token(val text: String, val kind: Kind)

    private val punctuation = Regex("[^a-z0-9'\\s]")
    private val spaces = Regex("\\s+")

    /** Lowercases and drops punctuation so "It's fine!" and "its fine" compare cleanly. */
    fun normalize(text: String): String =
        text.lowercase()
            .replace("’", "'")
            .replace(punctuation, " ")
            .replace(spaces, " ")
            .trim()

    /** Whitespace separated words exactly as they were written, punctuation included. */
    fun displayWords(text: String): List<String> =
        text.split(spaces).filter { it.isNotBlank() }

    fun words(text: String): List<String> = normalize(text).split(' ').filter { it.isNotBlank() }

    /** Comparison key for a single written word; punctuation-only tokens are dropped. */
    private fun key(token: String): String = normalize(token)

    private fun comparable(text: String): List<Pair<String, String>> =
        displayWords(text).map { it to key(it) }.filter { it.second.isNotBlank() }

    /**
     * Aligns the expected sentence with what the learner typed using a longest-common-subsequence
     * walk. MATCH tokens are correct words, MISSING ones were left out, EXTRA ones were added.
     * Words keep their original spelling in the result.
     */
    fun compare(expected: String, actual: String): List<Token> {
        val a = comparable(expected)
        val b = comparable(actual)
        val n = a.size
        val m = b.size
        val lcs = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                lcs[i][j] = if (a[i].second == b[j].second) {
                    lcs[i + 1][j + 1] + 1
                } else {
                    maxOf(lcs[i + 1][j], lcs[i][j + 1])
                }
            }
        }

        val out = ArrayList<Token>(maxOf(n, m))
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i].second == b[j].second -> {
                    out.add(Token(a[i].first, Kind.MATCH))
                    i++
                    j++
                }
                lcs[i + 1][j] >= lcs[i][j + 1] -> {
                    out.add(Token(a[i].first, Kind.MISSING))
                    i++
                }
                else -> {
                    out.add(Token(b[j].first, Kind.EXTRA))
                    j++
                }
            }
        }
        while (i < n) {
            out.add(Token(a[i].first, Kind.MISSING))
            i++
        }
        while (j < m) {
            out.add(Token(b[j].first, Kind.EXTRA))
            j++
        }
        return out
    }

    /** Fraction of the expected words the learner got right, 0f..1f. */
    fun accuracy(expected: String, actual: String): Float {
        val expectedCount = comparable(expected).size
        if (expectedCount == 0) return 0f
        val matched = compare(expected, actual).count { it.kind == Kind.MATCH }
        return (matched.toFloat() / expectedCount).coerceIn(0f, 1f)
    }

    fun isAcceptable(expected: String, actual: String): Boolean =
        normalize(expected) == normalize(actual) || accuracy(expected, actual) >= 0.9f

    /** "h____ w____" style hint that reveals the first letter of each word. */
    fun hint(text: String): String =
        displayWords(text).joinToString(" ") { word ->
            val trimmed = word.trim { !it.isLetterOrDigit() }
            if (trimmed.length <= 1) word else trimmed.first() + "_".repeat(trimmed.length - 1)
        }
}
