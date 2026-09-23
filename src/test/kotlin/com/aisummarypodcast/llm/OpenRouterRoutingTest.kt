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
import org.springframework.ai.openai.OpenAiChatOptions

class OpenRouterRoutingTest {

    private fun appProperties(effort: String = "medium") = AppProperties(
        llm = LlmProperties(),
        briefing = BriefingProperties(defaultTemperature = 0.9),
        episodes = EpisodesProperties(),
        feed = FeedProperties(),
        encryption = EncryptionProperties(masterKey = "test"),
        compose = ComposeProperties(maxOutputTokens = 96000, reasoningEffort = effort)
    )

    private fun model(provider: String, reasoningEffort: String = OpenRouterRouting.NO_REASONING) =
        ResolvedModel(
            provider = provider, model = "z-ai/glm-5.3", cost = null, stage = PipelineStage.COMPOSE,
            reasoningEffort = reasoningEffort
        )

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
    fun `an open-weight model keeps the floor when the model is named`() {
        val block = providerBlock(OpenRouterRouting.extraBodyFor("openrouter", "medium", model = "deepseek/deepseek-v4.1-flash"))

        assertEquals(listOf("fp8", "fp16", "bf16", "fp32"), block?.get("quantizations"))
        assertEquals(true, block?.get("require_parameters"))
    }

    @Test
    fun `a closed-weight vendor model is routed without the quantization floor`() {
        for (model in listOf("openai/gpt-6-luna", "anthropic/claude-sonnet-5")) {
            val block = providerBlock(OpenRouterRouting.extraBodyFor("openrouter", "none", model = model))

            assertNull(block?.get("quantizations"), model)
            assertEquals(true, block?.get("require_parameters"), model)
        }
    }

    @Test
    fun `a resolved closed-weight model drops the floor through the builder`() {
        val resolved = ResolvedModel(provider = "openrouter", model = "openai/gpt-6-luna", cost = null, stage = PipelineStage.COMPOSE)
        val body = OpenAiChatOptions.builder().withRoutingAndReasoning(resolved).build().extraBody

        assertNull(providerBlock(body)?.get("quantizations"))
        assertEquals("none", reasoningBlock(body)?.get("effort"))
    }

    @Test
    fun `an openai model sends no temperature, any other model keeps it`() {
        fun temperatureFor(model: String) = OpenAiChatOptions.builder().temperature(0.9)
            .withRoutingAndReasoning(ResolvedModel(provider = "openrouter", model = model, cost = null, stage = PipelineStage.COMPOSE))
            .build().temperature

        assertNull(temperatureFor("openai/gpt-6-luna"))
        assertEquals(0.9, temperatureFor("deepseek/deepseek-v4.1-flash"))
        assertEquals(0.9, temperatureFor("anthropic/claude-sonnet-5"))
    }

    @Test
    fun `the stage timeout is sent on the request options`() {
        // Spring AI 2.0.1 sends the options' timeout per request, and an unset one defaults to 60s,
        // replacing the client's stage timeout.
        val resolved = ResolvedModel(
            provider = "openrouter", model = "deepseek/deepseek-v4.1-flash", cost = null,
            stage = PipelineStage.COMPOSE, requestTimeout = java.time.Duration.ofMinutes(5)
        )

        val options = OpenAiChatOptions.builder().withRoutingAndReasoning(resolved).build()

        assertEquals(java.time.Duration.ofMinutes(5), options.timeout)
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
        val options = buildComposeOptions(model("openrouter", "medium"), podcast(), appProperties()).build()

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
            model("openrouter", "low"), podcast(mapOf("reasoningEffort" to "low")), appProperties()
        ).build()

        assertEquals("low", reasoningBlock(options.extraBody)?.get("effort"))
    }

    @Test
    fun `a blank podcast override falls back to the configured effort`() {
        val options = buildComposeOptions(
            model("openrouter", "high"), podcast(mapOf("reasoningEffort" to "  ")), appProperties(effort = "high")
        ).build()

        assertEquals("high", reasoningBlock(options.extraBody)?.get("effort"))
    }

    @Test
    fun `an effort of none is stated explicitly rather than omitted`() {
        val options = buildComposeOptions(model("openrouter"), podcast(), appProperties(effort = "none")).build()

        // Omitting the block does not mean "no reasoning" — OpenRouter infers the model's own
        // default, and the dedup model's is high effort. Measured on the live API: no block cost 47
        // reasoning tokens, effort "none" cost 0.
        assertEquals("none", reasoningBlock(options.extraBody)?.get("effort"))
        assertEquals(true, providerBlock(options.extraBody)?.get("require_parameters"))
    }

    @Test
    fun `the structured stages suppress reasoning alongside the floor`() {
        // The stages that pass NO_REASONING must still carry the provider floor, not replace it.
        val extraBody = OpenRouterRouting.extraBodyFor("openrouter", OpenRouterRouting.NO_REASONING)

        assertEquals("none", reasoningBlock(extraBody)?.get("effort"))
        assertEquals(true, reasoningBlock(extraBody)?.get("exclude"))
        assertEquals(true, providerBlock(extraBody)?.get("require_parameters"))
    }

    @Test
    fun `reasoning text is excluded from the response`() {
        val options = buildComposeOptions(model("openrouter", "medium"), podcast(), appProperties()).build()

        // The tokens are billed either way and openai-java has no field for message.reasoning, so
        // returning it would only risk it being mistaken for the script.
        assertEquals(true, reasoningBlock(options.extraBody)?.get("exclude"))
    }

    @Test
    fun `compose options for a direct openai model carry no routing block`() {
        val options = buildComposeOptions(model("openai", "medium"), podcast(), appProperties()).build()

        assertTrue(options.extraBody.isNullOrEmpty())
        // On a direct OpenAI call the flat field is the real one.
        assertEquals("medium", options.reasoningEffort)
    }
}
