package com.aisummarypodcast.llm

import com.aisummarypodcast.config.ComposeProperties
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The compose output ceiling as a standalone invariant. How the options are assembled — model,
 * temperature, reasoning effort and the OpenRouter routing floor — is covered by
 * [OpenRouterRoutingTest].
 */
class ComposeOptionsTest {

    @Test
    fun `the default ceiling clears observed compose usage and stays under the model window`() {
        // Compose output across 59 episodes peaked at 57,546 tokens; the model window is 131,072.
        // A ceiling inside that band bounds a runaway response without truncating a real script.
        val default = ComposeProperties().maxOutputTokens

        assertTrue(default > 57_546, "ceiling must clear the largest observed compose output")
        assertTrue(default < 131_072, "ceiling must stay below the model output window")
    }

    @Test
    fun `the default reasoning effort is stated rather than left to the provider`() {
        // An empty effort would hand the decision back to whichever endpoint OpenRouter picked,
        // which is what made compose swing between 6,048 and 72,821 output tokens.
        assertTrue(ComposeProperties().reasoningEffort.isNotBlank())
    }
}
