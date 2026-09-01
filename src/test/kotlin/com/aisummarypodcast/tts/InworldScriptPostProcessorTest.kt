package com.aisummarypodcast.tts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `stripLeadingInstruction removes a leading delivery instruction`() {
        assertEquals(
            "Picture someone sitting at home with a laptop.",
            InworldScriptPostProcessor.stripLeadingInstruction("[with quiet awe] Picture someone sitting at home with a laptop.")
        )
    }

    @Test
    fun `stripLeadingInstruction keeps instructions that are not leading`() {
        val input = "Picture this. [brightening] And then it shipped."
        assertEquals(input, InworldScriptPostProcessor.stripLeadingInstruction(input))
    }

    @Test
    fun `stripLeadingInstruction keeps a leading sound tag`() {
        val input = "[laugh] I can't believe it."
        assertEquals(input, InworldScriptPostProcessor.stripLeadingInstruction(input))
    }

    @Test
    fun `stripLeadingInstruction leaves untagged text untouched`() {
        val input = "Welcome to the show."
        assertEquals(input, InworldScriptPostProcessor.stripLeadingInstruction(input))
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

    // --- Flattening delivery directions ---------------------------------------------------------

    @Test
    fun `a bare deadpan direction is dropped`() {
        val result = InworldScriptPostProcessor.process("[deadpan] They caught visible mistakes.", retainSteeringInstructions = true)

        assertEquals("They caught visible mistakes.", result)
    }

    @Test
    fun `episode 194's turn comes back with the cue gone and the words intact`() {
        val turn = "[deadpan] They caught visible mistakes, like a button parked in the wrong spot, " +
            "but the gains didn't survive statistical correction."

        val result = InworldScriptPostProcessor.process(turn, retainSteeringInstructions = true)

        assertEquals(
            "They caught visible mistakes, like a button parked in the wrong spot, " +
                "but the gains didn't survive statistical correction.",
            result
        )
    }

    @Test
    fun `a flattening word inside a phrase is dropped`() {
        for (cue in listOf("in a deadpan tone", "flat and bored", "monotone, almost robotic", "whispering")) {
            val result = InworldScriptPostProcessor.process("[$cue] Something happened.", retainSteeringInstructions = true)
            assertEquals("Something happened.", result, "cue [$cue] should have been dropped")
        }
    }

    @Test
    fun `expressive directions are still forwarded`() {
        for (cue in listOf(
            "warm and conversational with an easy pace", "playful", "bright and quick",
            "with quiet awe", "with barely contained glee",
        )) {
            val result = InworldScriptPostProcessor.process("[$cue] Something happened.", retainSteeringInstructions = true)
            assertEquals("[$cue] Something happened.", result, "cue [$cue] should have been kept")
        }
    }

    @Test
    fun `reset is not treated as a flattening direction`() {
        val result = InworldScriptPostProcessor.process("[reset] Back to normal.", retainSteeringInstructions = true)

        assertEquals("[reset] Back to normal.", result)
    }

    @Test
    fun `sound tags are unaffected by the suppression rule`() {
        val result = InworldScriptPostProcessor.process("[sigh] Well. [laugh] Fine.", retainSteeringInstructions = true)

        assertEquals("[sigh] Well. [laugh] Fine.", result)
    }

    @Test
    fun `flattensDelivery matches whole words only`() {
        assertTrue(InworldScriptPostProcessor.flattensDelivery("deadpan"))
        assertTrue(InworldScriptPostProcessor.flattensDelivery("in a DEADPAN tone"))
        assertFalse(InworldScriptPostProcessor.flattensDelivery("warm and conversational"))
        // "deadpanning" is not the listed word, and a substring match would wrongly catch it.
        assertFalse(InworldScriptPostProcessor.flattensDelivery("flattered"))
        assertFalse(InworldScriptPostProcessor.flattensDelivery("shoutout energy"))
    }

    @Test
    fun `a dropped cue cannot survive into a chunk via re-emission`() {
        // Suppression happens in process, before chunking and before steering re-emission, so the
        // instruction is gone from the text the steering pass ever sees.
        val processed = InworldScriptPostProcessor.process(
            "[deadpan] First sentence. Second sentence.", retainSteeringInstructions = true
        )
        val chunks = InworldSteering.reemitInstructions(listOf(processed, "A later chunk."))

        assertTrue(chunks.none { it.contains("deadpan") }, "no chunk should carry the dropped cue")
    }

    @Test
    fun `a model without steering support still strips every direction`() {
        val result = InworldScriptPostProcessor.process("[deadpan] Words.", retainSteeringInstructions = false)

        assertEquals("Words.", result)
    }
}
