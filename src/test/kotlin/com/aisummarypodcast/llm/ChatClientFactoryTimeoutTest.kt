package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.BriefingProperties
import com.aisummarypodcast.config.EncryptionProperties
import com.aisummarypodcast.config.EpisodesProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.LlmProperties
import com.aisummarypodcast.config.StageTimeouts
import com.aisummarypodcast.store.LlmCacheRepository
import com.aisummarypodcast.user.UserProviderConfigService
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class ChatClientFactoryTimeoutTest {

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
        llmCallLogService = mockk<LlmCallLogService>(relaxed = true),
        appProperties = appProperties
    )

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
    }

    @Test
    fun `a borrowed model keeps its stage timeout but records under its own stage`() {
        val plan = ResolvedModel("openrouter", "m", null, PipelineStage.FILTER).copy(telemetryStage = RESEARCH_PLAN_STAGE)

        assertEquals(PipelineStage.FILTER, plan.stage)
        assertEquals("research-plan", plan.telemetryStage)
        assertEquals("filter", ResolvedModel("openrouter", "m", null, PipelineStage.FILTER).telemetryStage)
    }
}
