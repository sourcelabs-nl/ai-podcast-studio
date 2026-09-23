package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.BriefingProperties
import com.aisummarypodcast.config.EncryptionProperties
import com.aisummarypodcast.config.EpisodesProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.LlmModelOverrides
import com.aisummarypodcast.config.LlmProperties
import com.aisummarypodcast.config.ModelCost
import com.aisummarypodcast.config.ModelReference
import com.aisummarypodcast.config.ModelType
import com.aisummarypodcast.config.StageDefaults
import com.aisummarypodcast.store.Podcast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ModelResolverTest {

    private val models = mapOf(
        "openrouter" to mapOf(
            "anthropic/claude-haiku-4.5" to ModelCost(type = ModelType.LLM, inputCostPerMtok = 0.20),
            "anthropic/claude-sonnet-4" to ModelCost(type = ModelType.LLM, inputCostPerMtok = 3.00),
            "anthropic/claude-opus-5" to ModelCost(
                type = ModelType.LLM, inputCostPerMtok = 5.00, selectable = false
            )
        ),
        "ollama" to mapOf(
            "llama3" to ModelCost(type = ModelType.LLM)
        )
    )

    private val appProperties = AppProperties(
        llm = LlmProperties(
            defaults = StageDefaults(
                filter = ModelReference("openrouter", "anthropic/claude-haiku-4.5"),
                compose = ModelReference("openrouter", "anthropic/claude-sonnet-4")
            )
        ),
        models = models,
        briefing = BriefingProperties(),
        episodes = EpisodesProperties(),
        feed = FeedProperties(),
        encryption = EncryptionProperties(masterKey = "test-key")
    )

    private val resolver = ModelResolver(appProperties)

    private val podcast = Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech")

    @Test
    fun `uses global default when podcast has no overrides`() {
        val filterModel = resolver.resolve(RunConfig.resolve(appProperties, podcast), PipelineStage.FILTER)
        val composeModel = resolver.resolve(RunConfig.resolve(appProperties, podcast), PipelineStage.COMPOSE)

        assertEquals("openrouter", filterModel.provider)
        assertEquals("anthropic/claude-haiku-4.5", filterModel.model)
        assertEquals("openrouter", composeModel.provider)
        assertEquals("anthropic/claude-sonnet-4", composeModel.model)
    }

    @Test
    fun `a resolved model carries its stage's request timeout`() {
        val runConfig = RunConfig.resolve(appProperties, podcast)

        assertEquals(appProperties.llm.timeouts.compose, resolver.resolve(runConfig, PipelineStage.COMPOSE).requestTimeout)
        assertEquals(appProperties.llm.timeouts.filter, resolver.resolve(runConfig, PipelineStage.FILTER).requestTimeout)
    }

    @Test
    fun `podcast override takes precedence over global default`() {
        val podcastWithOverride = podcast.copy(
            llmModels = LlmModelOverrides(mapOf("compose" to ModelReference("ollama", "llama3")))
        )

        val composeModel = resolver.resolve(RunConfig.resolve(appProperties, podcastWithOverride), PipelineStage.COMPOSE)

        assertEquals("ollama", composeModel.provider)
        assertEquals("llama3", composeModel.model)
    }

    @Test
    fun `partial override only affects specified stage`() {
        val podcastWithOverride = podcast.copy(
            llmModels = LlmModelOverrides(mapOf("compose" to ModelReference("ollama", "llama3")))
        )

        val filterModel = resolver.resolve(RunConfig.resolve(appProperties, podcastWithOverride), PipelineStage.FILTER)
        val composeModel = resolver.resolve(RunConfig.resolve(appProperties, podcastWithOverride), PipelineStage.COMPOSE)

        assertEquals("anthropic/claude-haiku-4.5", filterModel.model)
        assertEquals("llama3", composeModel.model)
    }

    @Test
    fun `resolves the cost of an unselectable model`() {
        // A model is marked unselectable when its endpoints can no longer serve a request, but its
        // pricing has to keep resolving: an episode already generated on it would otherwise lose its
        // cost. Withdrawing the choice must not withdraw the price.
        val podcastOnWithheldModel = podcast.copy(
            llmModels = LlmModelOverrides(
                mapOf("filter" to ModelReference("openrouter", "anthropic/claude-opus-5"))
            )
        )

        val result = resolver.resolve(RunConfig.resolve(appProperties, podcastOnWithheldModel), PipelineStage.FILTER)

        assertEquals("anthropic/claude-opus-5", result.model)
        assertEquals(5.00, result.cost?.inputCostPerMtok)
    }

    @Test
    fun `returns null cost for unknown model`() {
        val podcastWithBadOverride = podcast.copy(
            llmModels = LlmModelOverrides(mapOf("filter" to ModelReference("openrouter", "nonexistent")))
        )

        val result = resolver.resolve(RunConfig.resolve(appProperties, podcastWithBadOverride), PipelineStage.FILTER)

        assertEquals("openrouter", result.provider)
        assertEquals("nonexistent", result.model)
        assertNull(result.cost)
    }
}
