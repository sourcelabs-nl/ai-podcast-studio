package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.LlmCallLogProperties
import com.aisummarypodcast.config.LlmProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class LlmCallLogCleanupTest {

    private val llmCallLogService = mockk<LlmCallLogService>(relaxed = true)
    private val appProperties = mockk<AppProperties> {
        every { llm } returns LlmProperties(callLog = LlmCallLogProperties(retention = Duration.ofDays(30)))
    }
    private val cleanup = LlmCallLogCleanup(llmCallLogService, appProperties)

    @Test
    fun `the cutoff is the configured retention behind now`() {
        val cutoff = slot<Instant>()
        every { llmCallLogService.deleteOlderThan(capture(cutoff)) } returns Unit

        val before = Instant.now().minus(Duration.ofDays(30))
        cleanup.cleanup()
        val after = Instant.now().minus(Duration.ofDays(30))

        assertTrue(!cutoff.captured.isBefore(before) && !cutoff.captured.isAfter(after))
    }
}
