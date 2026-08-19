package com.aisummarypodcast.podcast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScriptSnippetTest {

    @Test
    fun `returns null when no term appears`() {
        assertNull(ScriptSnippet.firstMatch(listOf("nothing relevant here"), listOf("java")))
    }

    @Test
    fun `returns null for blank and absent texts`() {
        assertNull(ScriptSnippet.firstMatch(listOf(null, "", "   "), listOf("java")))
    }

    @Test
    fun `returns null when there are no terms`() {
        assertNull(ScriptSnippet.firstMatch(listOf("a script mentioning java"), emptyList()))
    }

    @Test
    fun `short text is returned whole without ellipses`() {
        val snippet = ScriptSnippet.firstMatch(listOf("We talked about Java today."), listOf("java"))
        assertEquals("We talked about Java today.", snippet)
    }

    @Test
    fun `matching is case-insensitive`() {
        val snippet = ScriptSnippet.firstMatch(listOf("We talked about JAVA today."), listOf("java"))
        assertEquals("We talked about JAVA today.", snippet)
    }

    @Test
    fun `speaker tags are removed`() {
        val snippet = ScriptSnippet.firstMatch(listOf("<expert>\nJava is back.\n</expert>"), listOf("java"))
        assertEquals("Java is back.", snippet)
    }

    @Test
    fun `long text is trimmed around the term with ellipses`() {
        val filler = "word ".repeat(60)
        val snippet = ScriptSnippet.firstMatch(listOf(filler + "Java matters. " + filler), listOf("java"))!!

        assertTrue(snippet.startsWith("..."), snippet)
        assertTrue(snippet.endsWith("..."), snippet)
        assertTrue(snippet.contains("Java matters."), snippet)
        // Bounded by the radius on each side rather than returning the whole script
        assertTrue(snippet.length < 250, "snippet was ${snippet.length} chars")
    }

    @Test
    fun `snippet does not start or end mid-word`() {
        val snippet = ScriptSnippet.firstMatch(
            listOf("alpha ".repeat(40) + "Java " + "omega ".repeat(40)),
            listOf("java")
        )!!
        val inner = snippet.removePrefix("...").removeSuffix("...")

        assertTrue(inner.startsWith("alpha"), inner.take(20))
        assertTrue(inner.endsWith("omega"), inner.takeLast(20))
    }

    @Test
    fun `does not match inside a longer word`() {
        assertNull(ScriptSnippet.firstMatch(listOf("JavaScript everywhere"), listOf("java")))
    }

    @Test
    fun `points at the whole-word occurrence rather than an earlier partial one`() {
        val snippet = ScriptSnippet.firstMatch(
            listOf("First we covered JavaScript, and later Java itself."),
            listOf("java")
        )!!
        // The evidence must be the real Java mention, not the JavaScript one that precedes it
        assertTrue(snippet.contains("Java itself"), snippet)
    }

    @Test
    fun `a digit does not end a word`() {
        val snippet = ScriptSnippet.firstMatch(listOf("The Qwen3.8 release landed."), listOf("qwen"))
        assertEquals("The Qwen3.8 release landed.", snippet)
    }

    @Test
    fun `earliest term wins when several match`() {
        val snippet = ScriptSnippet.firstMatch(listOf("Kotlin first, then Java."), listOf("java", "kotlin"))
        assertEquals("Kotlin first, then Java.", snippet)
    }

    @Test
    fun `earlier source wins over a later one`() {
        val snippet = ScriptSnippet.firstMatch(listOf("script says java", "recap also says java"), listOf("java"))
        assertEquals("script says java", snippet)
    }

    @Test
    fun `falls through to a later source when the first has no match`() {
        val snippet = ScriptSnippet.firstMatch(listOf("script is silent", "the recap says java"), listOf("java"))
        assertEquals("the recap says java", snippet)
    }
}
