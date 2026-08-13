package com.aisummarypodcast.tts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InworldSteeringTest {

    @Test
    fun `only inworld-tts-2 supports steering`() {
        assertTrue(InworldSteering.supportsSteering("inworld-tts-2"))
        assertFalse(InworldSteering.supportsSteering("inworld-tts-1.5-max"))
        assertFalse(InworldSteering.supportsSteering("inworld-tts-2-flash"))
    }

    @Test
    fun `re-emits the active instruction on following chunks`() {
        val chunks = listOf("[warm and conversational] First.", "Second.", "Third.")

        assertEquals(
            listOf(
                "[warm and conversational] First.",
                "[warm and conversational] Second.",
                "[warm and conversational] Third."
            ),
            InworldSteering.reemitInstructions(chunks)
        )
    }

    @Test
    fun `a new instruction replaces the active one`() {
        val chunks = listOf("[calm] First.", "[brisk and urgent] Second.", "Third.")

        assertEquals(
            listOf("[calm] First.", "[brisk and urgent] Second.", "[brisk and urgent] Third."),
            InworldSteering.reemitInstructions(chunks)
        )
    }

    @Test
    fun `reset clears the active instruction`() {
        val chunks = listOf("[calm] First.", "[reset] Second.", "Third.")

        assertEquals(
            listOf("[calm] First.", "[reset] Second.", "Third."),
            InworldSteering.reemitInstructions(chunks)
        )
    }

    @Test
    fun `a chunk already opening with an instruction is not prefixed`() {
        val chunks = listOf("[calm] First.", "[calm] Second.")

        assertEquals(chunks, InworldSteering.reemitInstructions(chunks))
    }

    @Test
    fun `sound tags do not become the active instruction`() {
        val chunks = listOf("[sigh] First.", "Second.")

        assertEquals(chunks, InworldSteering.reemitInstructions(chunks))
    }

    @Test
    fun `an instruction later in a chunk still carries to the next chunk`() {
        val chunks = listOf("First. [brisk] More text.", "Second.")

        assertEquals(listOf("First. [brisk] More text.", "[brisk] Second."), InworldSteering.reemitInstructions(chunks))
    }

    @Test
    fun `no instruction leaves chunks untouched`() {
        val chunks = listOf("First.", "Second.")

        assertEquals(chunks, InworldSteering.reemitInstructions(chunks))
    }
}
