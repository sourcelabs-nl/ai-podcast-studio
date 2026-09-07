package com.aisummarypodcast.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfigMappersTest {

    private fun llm(selectable: Boolean = true) = ModelCost(
        type = ModelType.LLM,
        inputCostPerMtok = 1.0,
        outputCostPerMtok = 2.0,
        selectable = selectable
    )

    private fun tts() = ModelCost(type = ModelType.TTS, costPerMillionChars = 10.0)

    @Test
    fun `an unselectable model is withheld from the picker`() {
        // Its pricing stays in the registry so episodes generated on it still resolve a cost; only
        // the choice is withdrawn, because every one of its endpoints is rejected by the floor.
        val models = mapOf(
            "openrouter" to mapOf(
                "z-ai/glm-5.3" to llm(),
                "anthropic/claude-opus-5" to llm(selectable = false)
            )
        )

        val selectable = models.toSelectableModels()

        assertEquals(listOf("z-ai/glm-5.3"), selectable.getValue("openrouter").map { it.name })
    }

    @Test
    fun `models are selectable by default`() {
        val models = mapOf("openrouter" to mapOf("deepseek/deepseek-v4-flash-0731" to llm()))

        assertEquals(
            listOf(AvailableModel(name = "deepseek/deepseek-v4-flash-0731", type = "llm")),
            models.toSelectableModels().getValue("openrouter")
        )
    }

    @Test
    fun `the provider key survives even when every model is withheld`() {
        // The frontend groups by provider, so an emptied provider must stay present rather than
        // vanish and shift which provider the settings page treats as active.
        val models = mapOf(
            "openai" to mapOf("openai/gpt-5.4-nano" to llm(selectable = false)),
            "inworld" to mapOf("inworld-tts-2" to tts())
        )

        val selectable = models.toSelectableModels()

        assertTrue(selectable.containsKey("openai"))
        assertTrue(selectable.getValue("openai").isEmpty())
        assertEquals(listOf("inworld-tts-2"), selectable.getValue("inworld").map { it.name })
    }

    @Test
    fun `the type is carried through in lower case for both model kinds`() {
        val models = mapOf(
            "openrouter" to mapOf("z-ai/glm-5.3" to llm()),
            "inworld" to mapOf("inworld-tts-2" to tts())
        )

        val selectable = models.toSelectableModels()

        assertEquals("llm", selectable.getValue("openrouter").single().type)
        assertEquals("tts", selectable.getValue("inworld").single().type)
    }
}
