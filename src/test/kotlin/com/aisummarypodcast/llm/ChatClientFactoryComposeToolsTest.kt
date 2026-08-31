package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.BriefingProperties
import com.aisummarypodcast.config.EncryptionProperties
import com.aisummarypodcast.config.EpisodesProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.LlmProperties
import com.aisummarypodcast.config.StageTimeouts
import com.aisummarypodcast.research.RESEARCH_TOOL_NAME
import com.aisummarypodcast.research.ResearchService
import com.aisummarypodcast.research.ResearchTool
import com.aisummarypodcast.store.LlmCacheRepository
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.user.UserProviderConfigService
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class ChatClientFactoryComposeToolsTest {

    private val timeouts = StageTimeouts(
        filter = Duration.ofMinutes(3),
        dedup = Duration.ofMinutes(5),
        compose = Duration.ofMinutes(20)
    )

    private val appProperties = AppProperties(
        llm = LlmProperties(timeouts = timeouts),
        briefing = BriefingProperties(),
        episodes = EpisodesProperties(),
        feed = FeedProperties(),
        encryption = EncryptionProperties(masterKey = "test")
    )

    private val factory = ChatClientFactory(
        providerConfigService = mockk<UserProviderConfigService>(),
        llmCacheRepository = mockk<LlmCacheRepository>(),
        episodeHistoryRepository = mockk<EpisodeHistoryRepository>(),
        researchService = mockk<ResearchService>(),
        appProperties = appProperties
    )

    private fun resolved(stage: PipelineStage) =
        ResolvedModel(provider = "openrouter", model = "m", cost = null, stage = stage)

    @Test
    fun `filter stage gets the filter timeout, not the compose one`() {
        assertEquals(Duration.ofMinutes(3), factory.timeoutFor(PipelineStage.FILTER))
    }

    @Test
    fun `dedup stage gets the dedup timeout`() {
        assertEquals(Duration.ofMinutes(5), factory.timeoutFor(PipelineStage.DEDUP))
    }

    @Test
    fun `compose keeps the long timeout it needs`() {
        assertEquals(Duration.ofMinutes(20), factory.timeoutFor(PipelineStage.COMPOSE))
    }

    @Test
    fun `every stage resolves to a distinct configured timeout`() {
        val byStage = PipelineStage.entries.associateWith { factory.timeoutFor(it) }
        assertEquals(3, byStage.values.toSet().size)
        // The bug this replaced: one blanket value shared by every stage.
        assertTrue(byStage.getValue(PipelineStage.FILTER) < byStage.getValue(PipelineStage.COMPOSE))
        assertEquals(resolved(PipelineStage.DEDUP).stage, PipelineStage.DEDUP)
    }

    private fun podcast(deepDive: Boolean) = Podcast(
        id = "p1", userId = "u1", name = "Pod", topic = "Tech", deepDiveEnabled = deepDive
    )

    @Test
    fun `deep-dive disabled registers only history tool`() {
        val budget = ToolBudget()
        val tools = factory.buildComposeTools("u1", podcast(deepDive = false), budget)

        assertEquals(1, tools.size)
        assertTrue(tools.any { it is HistoryLookupTool })
        assertFalse(tools.any { it is ResearchTool })

        // History cap exhausts after HISTORY_LOOKUP_TOOL_CAP calls.
        repeat(HISTORY_LOOKUP_TOOL_CAP) { assertTrue(budget.tryConsume(HISTORY_LOOKUP_TOOL_NAME)) }
        assertFalse(budget.tryConsume(HISTORY_LOOKUP_TOOL_NAME))

        // webSearch was never registered — tryConsume returns true (no cap).
        assertTrue(budget.tryConsume(RESEARCH_TOOL_NAME))
        assertEquals(0, budget.invocations(RESEARCH_TOOL_NAME))
    }

    @Test
    fun `deep-dive enabled registers history and research tools`() {
        val budget = ToolBudget()
        val tools = factory.buildComposeTools("u1", podcast(deepDive = true), budget)

        assertEquals(2, tools.size)
        assertTrue(tools.any { it is HistoryLookupTool })
        assertTrue(tools.any { it is ResearchTool })

        // Research cap is enforced.
        repeat(com.aisummarypodcast.research.RESEARCH_TOOL_CAP) {
            assertTrue(budget.tryConsume(RESEARCH_TOOL_NAME))
        }
        assertFalse(budget.tryConsume(RESEARCH_TOOL_NAME))
    }
}
