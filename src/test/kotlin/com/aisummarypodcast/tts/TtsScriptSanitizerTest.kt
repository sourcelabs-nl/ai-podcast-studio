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
}
