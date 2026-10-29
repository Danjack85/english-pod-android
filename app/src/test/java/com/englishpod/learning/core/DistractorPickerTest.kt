package com.englishpod.learning.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DistractorPickerTest {

    private val pool = listOf(
        "supermarket", "superman", "supervise", "superb", "market",
        "grocery", "shopping", "cashier", "discount", "receipt",
        "appointment", "argument", "apartment", "equipment", "envelope",
    )

    @Test
    fun picksThreeDistinctNonAnswerOptions() {
        val picks = DistractorPicker.pick("supermarket", pool, 3)
        assertEquals(3, picks.size)
        assertEquals(3, picks.map { it.lowercase() }.distinct().size)
        assertTrue("supermarket" !in picks.map { it.lowercase() })
    }

    @Test
    fun prefersSimilarWords() {
        // Every option comes from the pool, and at least one should be a near neighbour.
        val picks = DistractorPicker.pick("supermarket", pool, 3)
        assertTrue(picks.all { it in pool })
        assertTrue(picks.any { it.startsWith("super") || it == "market" })
    }

    @Test
    fun answerIsNeverReturned() {
        repeat(20) {
            val picks = DistractorPicker.pick("cashier", pool, 3)
            assertTrue("cashier" !in picks.map { it.lowercase() })
        }
    }

    @Test
    fun smallPoolReturnsFewerOptions() {
        assertTrue(DistractorPicker.pick("word", emptyList(), 3).isEmpty())
        assertEquals(listOf("worm"), DistractorPicker.pick("word", listOf("worm"), 3))
    }

    @Test
    fun deterministicWithSeed() {
        val random = kotlin.random.Random(42)
        val a = DistractorPicker.pick("receipt", pool, 3, random)
        val b = DistractorPicker.pick("receipt", pool, 3, kotlin.random.Random(42))
        assertEquals(a, b)
    }

    @Test
    fun levenshteinBasics() {
        assertEquals(0, DistractorPicker.levenshtein("kitten", "kitten"))
        assertEquals(3, DistractorPicker.levenshtein("kitten", "sitting"))
        assertEquals(1, DistractorPicker.levenshtein("supermarket", "supermarkets"))
        assertNotEquals(0, DistractorPicker.levenshtein("cat", "dog"))
    }
}
