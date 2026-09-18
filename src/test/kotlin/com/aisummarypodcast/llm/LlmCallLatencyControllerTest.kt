package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration

@WebMvcTest(LlmCallLatencyController::class)
class LlmCallLatencyControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var llmCallLatencyService: LlmCallLatencyService

    @MockkBean(relaxed = true)
    private lateinit var appProperties: AppProperties

    @Test
    fun `latency reports every stage, including one that issued nothing`() {
        every { llmCallLatencyService.latencySince(Duration.ofDays(7)) } returns LlmCallLatencyResponse(
            since = "2026-09-08T10:00:00Z",
            stages = listOf(
                StageLatencyResponse("filter", 180, 900, 1800, 2200, 4000, 180_000),
                StageLatencyResponse("compose", 0, null, null, null, null, 1_200_000)
            )
        )

        mockMvc.perform(get("/llm/calls/latency"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.stages[0].stage").value("filter"))
            .andExpect(jsonPath("$.stages[0].samples").value(180))
            .andExpect(jsonPath("$.stages[0].p95Ms").value(2200))
            .andExpect(jsonPath("$.stages[1].samples").value(0))
            .andExpect(jsonPath("$.stages[1].p95Ms").doesNotExist())
            .andExpect(jsonPath("$.stages[1].timeoutMs").value(1_200_000))
    }

    @Test
    fun `a window of less than a day is rejected`() {
        mockMvc.perform(get("/llm/calls/latency").param("days", "0"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `an episode's percentiles are read for that episode and carry no window`() {
        every { llmCallLatencyService.latencyForEpisode(224) } returns LlmCallLatencyResponse(
            since = null,
            stages = listOf(StageLatencyResponse("compose", 11, 59_300, 445_000, 1_008_000, 1_008_000, 1_200_000))
        )

        mockMvc.perform(get("/llm/calls/latency").param("episodeId", "224"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.since").doesNotExist())
            .andExpect(jsonPath("$.stages[0].samples").value(11))

        verify(exactly = 0) { llmCallLatencyService.latencySince(any()) }
    }

    @Test
    fun `an episode's requests are listed individually`() {
        every { llmCallLatencyService.requestsForEpisode(224) } returns EpisodeLlmCallsResponse(
            episodeId = 224,
            predatesAttribution = false,
            requests = listOf(
                LlmCallResponse("2026-09-18T10:00:00Z", "compose", "test-model", 59_300, "ok", false),
                LlmCallResponse("2026-09-18T10:02:00Z", "filter", "test-model", 0, "ok", true)
            )
        )

        mockMvc.perform(get("/llm/calls/episodes/224"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.predatesAttribution").value(false))
            .andExpect(jsonPath("$.requests[0].durationMs").value(59_300))
            .andExpect(jsonPath("$.requests[1].cacheHit").value(true))
    }

    @Test
    fun `an episode generated before attribution existed says so`() {
        every { llmCallLatencyService.requestsForEpisode(12) } returns EpisodeLlmCallsResponse(
            episodeId = 12,
            predatesAttribution = true,
            requests = emptyList()
        )

        mockMvc.perform(get("/llm/calls/episodes/12"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.predatesAttribution").value(true))
            .andExpect(jsonPath("$.requests").isEmpty)
    }
}
