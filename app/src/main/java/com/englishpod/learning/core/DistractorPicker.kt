package com.englishpod.learning.core

import kotlin.math.min

/**
 * Picks believable wrong answers for multiple-choice vocabulary drills.
 *
 * Random distractors are trivially easy to eliminate, so candidates are ranked by similarity to
 * the target word: edit distance first, then shared prefixes/suffixes, then word length.
 */
object DistractorPicker {

    /** Candidates must differ from the answer and from each other. */
    fun pick(
        answer: String,
        pool: Collection<String>,
        count: Int = 3,
        random: kotlin.random.Random = kotlin.random.Random.Default,
    ): List<String> {
        val answerKey = answer.trim().lowercase()
        if (count <= 0) return emptyList()
        val candidates = pool
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.lowercase() != answerKey }
            .distinctBy { it.lowercase() }
        if (candidates.isEmpty()) return emptyList()

        val ranked = candidates
            .map { it to similarity(answerKey, it.lowercase()) }
            .sortedByDescending { it.second }
        // Take from the similar head of the list, jittered so the same word does not always
        // produce the identical option set.
        val headSize = min(ranked.size, (count * 4).coerceAtLeast(8))
        val head = ranked.take(headSize)
        val tail = ranked.drop(headSize)

        val chosen = mutableListOf<String>()
        if (head.isNotEmpty()) {
            val topThird = head.take((head.size / 3).coerceAtLeast(1))
            chosen.add(topThird[random.nextInt(topThird.size)].first)
        }
        for (group in listOf(head, tail)) {
            for (candidate in group.shuffled(random)) {
                if (chosen.size >= count) break
                val text = candidate.first
                if (text.lowercase() !in chosen.map { it.lowercase() }) chosen.add(text)
            }
            if (chosen.size >= count) break
        }
        return chosen.take(count)
    }

    /** 0f..1f; exact match = 1. Comparing against the lowercase key is intentional. */
    private fun similarity(a: String, b: String): Float {
        val distance = levenshtein(a, b)
        val shape = 1f - distance.toFloat() / maxOf(a.length, b.length, 1)
        val prefix = commonPrefixLength(a, b).coerceAtMost(3) / 3f
        val suffix = commonSuffixLength(a, b).coerceAtMost(3) / 3f
        val length = 1f - kotlin.math.abs(a.length - b.length) / maxOf(a.length, b.length, 1).toFloat()
        return shape * 0.55f + prefix * 0.15f + suffix * 0.15f + length * 0.15f
    }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        val current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost)
            }
            System.arraycopy(current, 0, previous, 0, current.size)
        }
        return previous[b.length]
    }

    private fun commonPrefixLength(a: String, b: String): Int {
        var i = 0
        while (i < a.length && i < b.length && a[i] == b[i]) i++
        return i
    }

    private fun commonSuffixLength(a: String, b: String): Int {
        var i = 0
        while (i < a.length && i < b.length && a[a.length - 1 - i] == b[b.length - 1 - i]) i++
        return i
    }
}
