package com.aisummarypodcast.tts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InworldScriptPostProcessorTest {

    @Test
    fun `converts double asterisks to single asterisks`() {
        val result = InworldScriptPostProcessor.process("This is **important** news.")
        assertEquals("This is *important* news.", result)
    }

    @Test
    fun `preserves single asterisks`() {
        val result = InworldScriptPostProcessor.process("This is *emphasized* text.")
        assertEquals("This is *emphasized* text.", result)
    }

    @Test
    fun `strips markdown headers`() {
        val result = InworldScriptPostProcessor.process("## Breaking News\nThe story begins here.")
        assertEquals("The story begins here.", result)
    }

    @Test
    fun `strips markdown bullet prefixes`() {
        val result = InworldScriptPostProcessor.process("- First item\n- Second item")
        assertEquals("First item\nSecond item", result)
    }

    @Test
    fun `does not strip emphasis asterisks at line start`() {
        val result = InworldScriptPostProcessor.process("*stressed word* in a sentence")
        assertEquals("*stressed word* in a sentence", result)
    }

    @Test
    fun `converts markdown links to plain text`() {
        val result = InworldScriptPostProcessor.process("Visit [Anthropic](https://anthropic.com) for more.")
        assertEquals("Visit Anthropic for more.", result)
    }

    @Test
    fun `strips emojis`() {
        val result = InworldScriptPostProcessor.process("Great news! 🎉 The update is here.")
        assertEquals("Great news! The update is here.", result)
    }

    @Test
    fun `preserves supported non-verbal tags`() {
        val result = InworldScriptPostProcessor.process("[sigh] I can't believe it.")
        assertEquals("[sigh] I can't believe it.", result)
    }

    @Test
    fun `preserves all documented sound names`() {
        val tags = listOf("[sigh]", "[laugh]", "[breathe]", "[cough]", "[clear throat]", "[yawn]")
        for (tag in tags) {
            val result = InworldScriptPostProcessor.process("$tag Hello.")
            assertEquals("$tag Hello.", result, "Tag $tag should be preserved")
        }
    }

    @Test
    fun `rewrites the legacy underscore spelling of clear throat`() {
        assertEquals("[clear throat] Right, moving on.", InworldScriptPostProcessor.process("[clear_throat] Right, moving on."))
    }

    @Test
    fun `sound names are preserved regardless of steering support`() {
        assertEquals("[sigh] Hello.", InworldScriptPostProcessor.process("[sigh] Hello.", retainSteeringInstructions = true))
    }

    @Test
    fun `strips unsupported tags`() {
        val result = InworldScriptPostProcessor.process("[cheerfully] Welcome to the show!")
        assertEquals("Welcome to the show!", result)
    }

    @Test
    fun `strips multiple unsupported tags`() {
        val result = InworldScriptPostProcessor.process("[excitedly] Hello [seriously] and goodbye.")
        assertEquals("Hello and goodbye.", result)
    }

    @Test
    fun `retains steering instructions when the model supports them`() {
        val result = InworldScriptPostProcessor.process(
            "[warm and conversational] Welcome to the show!",
            retainSteeringInstructions = true
        )
        assertEquals("[warm and conversational] Welcome to the show!", result)
    }

    @Test
    fun `strips steering instructions when the model does not support them`() {
        val result = InworldScriptPostProcessor.process("[warm and conversational] Welcome to the show!")
        assertEquals("Welcome to the show!", result)
    }

    @Test
    fun `single-word and multi-word instructions are treated the same`() {
        val input = "[excited] Hello [say excitedly] there."
        assertEquals("Hello there.", InworldScriptPostProcessor.process(input))
        assertEquals(input, InworldScriptPostProcessor.process(input, retainSteeringInstructions = true))
    }

    @Test
    fun `reset follows the steering rule`() {
        assertEquals("Back to normal.", InworldScriptPostProcessor.process("[reset] Back to normal."))
        assertEquals(
            "[reset] Back to normal.",
            InworldScriptPostProcessor.process("[reset] Back to normal.", retainSteeringInstructions = true)
        )
    }

    @Test
    fun `non-alphabetic tags are always stripped`() {
        assertEquals("A citation marker.", InworldScriptPostProcessor.process("A citation marker.[1]"))
        assertEquals(
            "A citation marker.",
            InworldScriptPostProcessor.process("A citation marker.[1]", retainSteeringInstructions = true)
        )
    }

    @Test
    fun `applies all transformations together`() {
        val input = "## Intro\n**Welcome** to the show! 🎉 [excitedly] Let's begin."
        val result = InworldScriptPostProcessor.process(input)
        assertEquals("*Welcome* to the show! Let's begin.", result)
    }

    @Test
    fun `handles empty string`() {
        assertEquals("", InworldScriptPostProcessor.process(""))
    }

    @Test
    fun `handles plain text without modifications`() {
        val input = "This is a normal sentence without any special formatting."
        assertEquals(input, InworldScriptPostProcessor.process(input))
    }

    @Test
    fun `strips star bullets but not emphasis`() {
        val result = InworldScriptPostProcessor.process("* Bullet item\n*emphasized* word")
        assertEquals("Bullet item\n*emphasized* word", result)
    }
}
