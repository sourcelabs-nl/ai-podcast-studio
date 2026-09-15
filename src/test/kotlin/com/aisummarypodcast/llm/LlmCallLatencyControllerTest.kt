package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
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
}
