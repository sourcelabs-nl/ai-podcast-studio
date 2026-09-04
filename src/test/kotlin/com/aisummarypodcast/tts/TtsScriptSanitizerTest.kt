package com.aisummarypodcast.tts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TtsScriptSanitizerTest {

    @Test
    fun `replaces em-dash with comma and space`() {
        val result = TtsScriptSanitizer.sanitize("foo — bar")
        assertEquals("foo , bar", result)
    }

    @Test
    fun `replaces en-dash with comma and space`() {
        val result = TtsScriptSanitizer.sanitize("foo – bar")
        assertEquals("foo , bar", result)
    }

    @Test
    fun `replaces multiple dashes`() {
        val result = TtsScriptSanitizer.sanitize("one — two – three — four")
        assertEquals("one , two , three , four", result)
    }

    @Test
    fun `collapses comma before sentence terminator`() {
        val result = TtsScriptSanitizer.sanitize("foo —. Next sentence.")
        assertEquals("foo. Next sentence.", result)
    }

    @Test
    fun `collapses comma before question mark`() {
        val result = TtsScriptSanitizer.sanitize("foo —? Really?")
        assertEquals("foo? Really?", result)
    }

    @Test
    fun `collapses comma before exclamation mark`() {
        val result = TtsScriptSanitizer.sanitize("foo —! Wow!")
        assertEquals("foo! Wow!", result)
    }

    @Test
    fun `passes through dash-free script unchanged`() {
        val script = "This is a normal sentence. Nothing to sanitize here."
        assertEquals(script, TtsScriptSanitizer.sanitize(script))
    }

    @Test
    fun `collapses duplicate spaces introduced by replacement`() {
        val result = TtsScriptSanitizer.sanitize("foo  —  bar")
        assertEquals("foo , bar", result)
    }

    // --- unlisted IPA spans ---

    private val dictionary = mapOf("Jarno" to "/j\u0251rno\u02d0/")

    @Test
    fun `keeps an IPA span that is in the pronunciation dictionary`() {
        val script = "It's less scary than it sounds, /j\u0251rno\u02d0/."
        assertEquals(script, TtsScriptSanitizer.sanitize(script, dictionary))
    }

    @Test
    fun `drops an IPA span the model invented for an unlisted name`() {
        // The dictionary held only Jarno; the model minted this one for the other speaker and the
        // engine read it out as a mispronounced name.
        val result = TtsScriptSanitizer.sanitize("And I'm /st\u025bfan/ ... I mean, Stephan.", dictionary)
        assertEquals("And I'm ... I mean, Stephan.", result)
    }

    @Test
    fun `drops every IPA span when no dictionary is configured`() {
        val result = TtsScriptSanitizer.sanitize("And I'm /st\u025bfan/ here.", emptyMap())
        assertEquals("And I'm here.", result)
    }

    @Test
    fun `leaves all-ASCII slash pairs alone`() {
        // Ordinary prose, not phonetics: stripping these would mangle the script.
        val script = "Use TCP/IP for input/output, and/or a queue, 24/7."
        assertEquals(script, TtsScriptSanitizer.sanitize(script, dictionary))
    }

    @Test
    fun `does not span across a line break`() {
        val script = "half a fraction 1/2\nand /or something/ else"
        assertEquals(script, TtsScriptSanitizer.sanitize(script, dictionary))
    }
}
