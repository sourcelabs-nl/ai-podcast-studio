package com.aisummarypodcast.llm

import com.aisummarypodcast.config.ComposeProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ComposeOptionsTest {

    @Test
    fun `compose options carry the configured output ceiling`() {
        val options = buildComposeOptions("z-ai/glm-5.2", 0.9, ComposeProperties(maxOutputTokens = 96000)).build()

        assertEquals(96000, options.maxTokens)
        assertEquals("z-ai/glm-5.2", options.model)
        assertEquals(0.9, options.temperature)
    }

    @Test
    fun `a custom ceiling is honoured`() {
        val options = buildComposeOptions("m", 0.5, ComposeProperties(maxOutputTokens = 32000)).build()

        assertEquals(32000, options.maxTokens)
    }

    @Test
    fun `the default ceiling clears observed compose usage and stays under the model window`() {
        // Compose output across 59 episodes peaked at 57,546 tokens; the model window is 131,072.
        // A ceiling inside that band bounds a runaway response without truncating a real script.
        val default = ComposeProperties().maxOutputTokens

        assertTrue(default > 57_546, "ceiling must clear the largest observed compose output")
        assertTrue(default < 131_072, "ceiling must stay below the model output window")
    }
}
