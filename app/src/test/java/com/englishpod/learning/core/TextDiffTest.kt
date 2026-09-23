package com.englishpod.learning.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextDiffTest {

    @Test
    fun identicalSentenceIsAllMatch() {
        val sentence = "hello everyone welcome back"
        val tokens = TextDiff.compare(sentence, sentence)
        assertEquals(sentence, TextDiff.normalize(sentence))
        assertTrue(tokens.all { it.kind == TextDiff.Kind.MATCH })
        assertEquals(1f, TextDiff.accuracy(sentence, sentence), 0.0001f)
    }

    @Test
    fun punctuationAndCaseAreIgnored() {
        assertEquals("it's fine", TextDiff.normalize("It's fine!"))
        assertTrue(TextDiff.isAcceptable("It's fine!", "it's fine"))
        assertEquals(1f, TextDiff.accuracy("It's fine!", "it's fine"), 0.0001f)
    }

    @Test
    fun missingWordsAreReportedAndKeepOriginalSpelling() {
        val expected = "Shopping Groceries at the Supermarket"
        val tokens = TextDiff.compare(expected, "Shopping at the Supermarket")
        val missing = tokens.filter { it.kind == TextDiff.Kind.MISSING }.map { it.text }
        assertEquals(listOf("Groceries"), missing)
        assertTrue(tokens.first { it.kind == TextDiff.Kind.MATCH }.text == "Shopping")
    }

    @Test
    fun extraWordsAreReported() {
        val tokens = TextDiff.compare("welcome back", "welcome right back")
        val extra = tokens.filter { it.kind == TextDiff.Kind.EXTRA }.map { it.text }
        assertEquals(listOf("right"), extra)
    }

    @Test
    fun accuracyReflectsPartialRecall() {
        val expected = "hello everyone welcome back English beginners"
        val actual = "hello everyone welcome back"
        assertEquals(4f / 6f, TextDiff.accuracy(expected, actual), 0.01f)
        assertFalse(TextDiff.isAcceptable(expected, actual))
    }

    @Test
    fun hintMasksAllButFirstLetter() {
        assertEquals("h" + "_".repeat(4) + " e" + "_".repeat(7), TextDiff.hint("hello everyone"))
        assertEquals("I" + "_".repeat(2) + " r" + "_".repeat(4), TextDiff.hint("I'm ready"))
        // Punctuation at the edges is ignored; the mask still covers the whole word.
        assertEquals("G" + "_".repeat(4) + " s" + "_".repeat(10), TextDiff.hint("Go-on, supermarket!"))
    }

    @Test
    fun emptyAnswerScoresZero() {
        assertEquals(0f, TextDiff.accuracy("hello everyone", ""), 0.0001f)
        assertEquals(0f, TextDiff.accuracy("", ""), 0.0001f)
    }
}
