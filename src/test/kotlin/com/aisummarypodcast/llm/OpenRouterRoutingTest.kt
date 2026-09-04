package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.BriefingProperties
import com.aisummarypodcast.config.ComposeProperties
import com.aisummarypodcast.config.EncryptionProperties
import com.aisummarypodcast.config.EpisodesProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.LlmProperties
import com.aisummarypodcast.store.Podcast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenRouterRoutingTest {

    private fun appProperties(effort: String = "medium") = AppProperties(
        llm = LlmProperties(),
        briefing = BriefingProperties(defaultTemperature = 0.9),
        episodes = EpisodesProperties(),
        feed = FeedProperties(),
        encryption = EncryptionProperties(masterKey = "test"),
        compose = ComposeProperties(maxOutputTokens = 96000, reasoningEffort = effort)
    )

    private fun model(provider: String) =
        ResolvedModel(provider = provider, model = "z-ai/glm-5.3", cost = null, stage = PipelineStage.COMPOSE)

    private fun podcast(settings: Map<String, String>? = null) =
        Podcast(id = "p1", userId = "u1", name = "T", topic = "t", composeSettings = settings)

    @Suppress("UNCHECKED_CAST")
    private fun providerBlock(extraBody: Map<String, Any>?) =
        extraBody?.get("provider") as? Map<String, Any>

    @Suppress("UNCHECKED_CAST")
    private fun reasoningBlock(extraBody: Map<String, Any>?) =
        extraBody?.get("reasoning") as? Map<String, Any>

    @Test
    fun `an openrouter model gets the routing floor`() {
        val block = providerBlock(OpenRouterRouting.extraBodyFor("openrouter", "medium"))

        assertEquals(listOf("fp8", "fp16", "bf16", "fp32"), block?.get("quantizations"))
        assertEquals(true, block?.get("require_parameters"))
    }

    @Test
    fun `the floor excludes the lossy quantizations that caused the trouble`() {
        @Suppress("UNCHECKED_CAST")
        val accepted = providerBlock(OpenRouterRouting.extraBodyFor("openrouter", "medium"))
            ?.get("quantizations") as List<String>

        // fp4 endpoints follow a long, format-sensitive prompt worse; "unknown" gives no assurance.
        for (rejected in listOf("fp4", "int4", "int8", "mxfp4", "nvfp4", "fp6", "unknown")) {
            assertFalse(rejected in accepted, "$rejected must not clear the floor")
        }
    }

    @Test
    fun `fallbacks are left enabled so one bad endpoint is not fatal`() {
        assertNull(providerBlock(OpenRouterRouting.extraBodyFor("openrouter", "medium"))?.get("allow_fallbacks"))
    }

    @Test
    fun `a non-openrouter provider gets nothing`() {
        assertTrue(OpenRouterRouting.extraBodyFor("openai", "medium").isEmpty())
        assertTrue(OpenRouterRouting.extraBodyFor("ollama", "medium").isEmpty())
    }

    // --- Compose options ------------------------------------------------------------------------

    @Test
    fun `compose options carry the configured reasoning effort and the floor`() {
        val options = buildComposeOptions(model("openrouter"), podcast(), appProperties()).build()

        // OpenRouter reads a `reasoning` object and, per its docs, does not accept OpenAI's flat
        // `reasoning_effort`; sending the flat field here had no effect at all.
        assertEquals("medium", reasoningBlock(options.extraBody)?.get("effort"))
        assertNull(options.reasoningEffort)
        assertEquals(96000, options.maxTokens)
        assertEquals("z-ai/glm-5.3", options.model)
        assertEquals(true, providerBlock(options.extraBody)?.get("require_parameters"))
    }

    @Test
    fun `a podcast overrides the reasoning effort`() {
        val options = buildComposeOptions(
            model("openrouter"), podcast(mapOf("reasoningEffort" to "low")), appProperties()
        ).build()

        assertEquals("low", reasoningBlock(options.extraBody)?.get("effort"))
    }

    @Test
    fun `a blank podcast override falls back to the configured effort`() {
        val options = buildComposeOptions(
            model("openrouter"), podcast(mapOf("reasoningEffort" to "  ")), appProperties(effort = "high")
        ).build()

        assertEquals("high", reasoningBlock(options.extraBody)?.get("effort"))
    }

    @Test
    fun `an effort of none sends no reasoning block at all`() {
        val options = buildComposeOptions(model("openrouter"), podcast(), appProperties(effort = "none")).build()

        // require_parameters restricts routing to endpoints supporting every parameter sent, so
        // asking a deliberately non-reasoning model to acknowledge one risks leaving no endpoint.
        assertNull(reasoningBlock(options.extraBody))
        assertEquals(true, providerBlock(options.extraBody)?.get("require_parameters"))
    }

    @Test
    fun `reasoning text is excluded from the response`() {
        val options = buildComposeOptions(model("openrouter"), podcast(), appProperties()).build()

        // The tokens are billed either way and openai-java has no field for message.reasoning, so
        // returning it would only risk it being mistaken for the script.
        assertEquals(true, reasoningBlock(options.extraBody)?.get("exclude"))
    }

    @Test
    fun `compose options for a direct openai model carry no routing block`() {
        val options = buildComposeOptions(model("openai"), podcast(), appProperties()).build()

        assertTrue(options.extraBody.isNullOrEmpty())
        // On a direct OpenAI call the flat field is the real one.
        assertEquals("medium", options.reasoningEffort)
    }
}
