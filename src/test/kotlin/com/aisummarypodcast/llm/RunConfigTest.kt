package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.BriefingProperties
import com.aisummarypodcast.config.ComposeProperties
import com.aisummarypodcast.config.EncryptionProperties
import com.aisummarypodcast.config.EpisodesProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.LlmModelOverrides
import com.aisummarypodcast.config.LlmProperties
import com.aisummarypodcast.config.ModelReference
import com.aisummarypodcast.config.StageDefaults
import com.aisummarypodcast.research.FOCUS_RESEARCH_QUERY_CAP
import com.aisummarypodcast.research.ResearchRequest
import com.aisummarypodcast.store.Podcast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RunConfigTest {

    private val defaultCompose = ModelReference("openrouter", "z-ai/glm-5.3")
    private val defaultFilter = ModelReference("openrouter", "deepseek/deepseek-v4-flash-0731")
    private val podcastCompose = ModelReference("openrouter", "z-ai/glm-5.3")
    private val overrideCompose = ModelReference("openrouter", "moonshotai/kimi-k2")

    private val appProperties = AppProperties(
        llm = LlmProperties(defaults = StageDefaults(filter = defaultFilter, compose = defaultCompose)),
        briefing = BriefingProperties(targetWords = 1500),
        episodes = EpisodesProperties(),
        feed = FeedProperties(),
        encryption = EncryptionProperties(masterKey = "test"),
        compose = ComposeProperties(reasoningEffort = "medium")
    )

    private val resolver = ModelResolver(appProperties)

    private fun podcast(
        llmModels: LlmModelOverrides? = null,
        composeSettings: Map<String, String>? = null,
        targetWords: Int? = null
    ) = Podcast(
        id = "p1", userId = "u1", name = "T", topic = "t",
        llmModels = llmModels, composeSettings = composeSettings, targetWords = targetWords
    )

    private val podcastWithSettings = podcast(
        llmModels = LlmModelOverrides(mapOf("compose" to podcastCompose)),
        composeSettings = mapOf("reasoningEffort" to "high"),
        targetWords = 900
    )

    @Test
    fun `app defaults apply to a podcast that sets nothing and a run without overrides`() {
        val config = RunConfig.resolve(appProperties, podcast())

        assertEquals(defaultCompose, config.modelFor(PipelineStage.COMPOSE))
        assertEquals(defaultFilter, config.modelFor(PipelineStage.FILTER))
        assertEquals("medium", config.reasoningEffortFor(PipelineStage.COMPOSE))
        PipelineStage.entries.filter { it != PipelineStage.COMPOSE }.forEach {
            assertEquals(OpenRouterRouting.NO_REASONING, config.reasoningEffortFor(it))
        }
        assertEquals(ProviderPreferences.DEFAULT, config.providerPreferences)
        assertEquals(1500, config.targetWords)
        assertNull(config.researchQueryCap)
        assertFalse(config.bypassLlmCache)
    }

    @Test
    fun `podcast settings take precedence over app defaults`() {
        val config = RunConfig.resolve(appProperties, podcastWithSettings)

        assertEquals(podcastCompose, config.modelFor(PipelineStage.COMPOSE))
        assertEquals(defaultFilter, config.modelFor(PipelineStage.FILTER))
        assertEquals("high", config.reasoningEffortFor(PipelineStage.COMPOSE))
        assertEquals(900, config.targetWords)
    }

    @Test
    fun `run overrides take precedence over podcast settings for every overridable field`() {
        val overrides = RunOverrides(
            models = mapOf(PipelineStage.COMPOSE to overrideCompose),
            reasoningEffort = mapOf(PipelineStage.COMPOSE to "low", PipelineStage.DEDUP to "minimal"),
            providerSort = "throughput",
            preferredMinThroughput = 50,
            targetWords = 600,
            researchQueryCap = 1,
            bypassLlmCache = true
        )

        val config = RunConfig.resolve(appProperties, podcastWithSettings, overrides)

        assertEquals(overrideCompose, config.modelFor(PipelineStage.COMPOSE))
        assertEquals(defaultFilter, config.modelFor(PipelineStage.FILTER))
        assertEquals("low", config.reasoningEffortFor(PipelineStage.COMPOSE))
        assertEquals("minimal", config.reasoningEffortFor(PipelineStage.DEDUP))
        assertEquals(OpenRouterRouting.NO_REASONING, config.reasoningEffortFor(PipelineStage.FILTER))
        assertEquals(ProviderPreferences(sort = "throughput", preferredMinThroughput = 50), config.providerPreferences)
        assertEquals(600, config.targetWords)
        assertEquals(1, config.researchQueryCap)
        assertTrue(config.bypassLlmCache)
        assertEquals(podcastCompose, podcastWithSettings.llmModels?.get("compose"), "the podcast's own setting is unchanged")
    }

    @Test
    fun `the resolved model carries the run's effort and provider preferences`() {
        val config = RunConfig.resolve(
            appProperties, podcast(),
            RunOverrides(models = mapOf(PipelineStage.FILTER to overrideCompose), providerSort = "latency")
        )

        val filter = resolver.resolve(config, PipelineStage.FILTER)

        assertEquals(overrideCompose.model, filter.model)
        assertEquals(OpenRouterRouting.NO_REASONING, filter.reasoningEffort)
        assertEquals("latency", filter.providerPreferences.sort)
    }

    @Test
    fun `a research query cap override replaces the default cap`() {
        val default = RunConfig.resolve(appProperties, podcast())
        val capped = RunConfig.resolve(appProperties, podcast(), RunOverrides(researchQueryCap = 2))

        assertEquals(FOCUS_RESEARCH_QUERY_CAP, ResearchRequest(podcast(), listOf("x"), default, focusEpisode = true).queryCap)
        assertEquals(2, ResearchRequest(podcast(), listOf("x"), capped, focusEpisode = true).queryCap)
    }

    // --- Byte-identical defaults: the request body a run without overrides sends is the one sent
    // before run configuration existed, for a podcast with and without its own settings.

    private val floor = mapOf(
        "quantizations" to listOf("fp8", "fp16", "bf16", "fp32"),
        "require_parameters" to true
    )

    private fun expectedBody(effort: String) =
        mapOf("provider" to floor, "reasoning" to mapOf("effort" to effort, "exclude" to true))

    private fun composeBody(podcast: Podcast, overrides: RunOverrides? = null): Map<String, Any>? {
        val model = resolver.resolve(RunConfig.resolve(appProperties, podcast, overrides), PipelineStage.COMPOSE)
        return buildComposeOptions(model, podcast, appProperties).build().extraBody
    }

    @Test
    fun `a podcast without settings sends the default compose model and body`() {
        val model = resolver.resolve(RunConfig.resolve(appProperties, podcast()), PipelineStage.COMPOSE)

        assertEquals(defaultCompose.model, model.model)
        assertEquals(expectedBody("medium"), composeBody(podcast()))
    }

    @Test
    fun `a podcast with its own settings sends its compose model and effort`() {
        val model = resolver.resolve(RunConfig.resolve(appProperties, podcastWithSettings), PipelineStage.COMPOSE)

        assertEquals(podcastCompose.model, model.model)
        assertEquals(expectedBody("high"), composeBody(podcastWithSettings))
    }

    @Test
    fun `non-compose stages still send no reasoning`() {
        PipelineStage.entries.filter { it != PipelineStage.COMPOSE }.forEach { stage ->
            val model = resolver.resolve(RunConfig.resolve(appProperties, podcastWithSettings), stage)
            val body = org.springframework.ai.openai.OpenAiChatOptions.builder()
                .withRoutingAndReasoning(model).build().extraBody
            assertEquals(expectedBody(OpenRouterRouting.NO_REASONING), body, "stage $stage")
        }
    }

    @Test
    fun `a provider sort and throughput override changes the compose request`() {
        val body = composeBody(podcast(), RunOverrides(providerSort = "throughput", preferredMinThroughput = 40))

        assertEquals(
            mapOf(
                "provider" to floor + mapOf("sort" to "throughput", "preferred_min_throughput" to 40),
                "reasoning" to mapOf("effort" to "medium", "exclude" to true)
            ),
            body
        )
    }
}
