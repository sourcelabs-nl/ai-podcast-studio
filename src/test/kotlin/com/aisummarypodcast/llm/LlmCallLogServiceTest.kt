package com.aisummarypodcast.llm

import com.aisummarypodcast.store.LlmCall
import com.aisummarypodcast.store.LlmCallRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessResourceFailureException
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

class LlmCallLogServiceTest {

    private val repository = mockk<LlmCallRepository>()
    private val service = LlmCallLogService(repository)

    private val record = LlmCallRecord(
        startedAt = Instant.parse("2026-09-15T10:00:00Z"),
        stage = "compose",
        provider = "openrouter",
        model = "test-model",
        duration = 1500.milliseconds,
        inputTokens = 100,
        outputTokens = 200,
        reportedCostUsd = 0.0042
    )

    @Test
    fun `a record is mapped onto the row it persists`() {
        val saved = slot<LlmCall>()
        every { repository.save(capture(saved)) } answers { saved.captured }

        service.record(record)

        assertEquals("2026-09-15T10:00:00Z", saved.captured.startedAt)
        assertEquals("compose", saved.captured.stage)
        assertEquals(1500L, saved.captured.durationMs)
        assertEquals("ok", saved.captured.outcome)
        assertEquals(false, saved.captured.cacheHit)
    }

    @Test
    fun `a failure outcome carries its exception type`() {
        val saved = slot<LlmCall>()
        every { repository.save(capture(saved)) } answers { saved.captured }

        service.record(
            record.copy(outcome = LlmCallOutcome.ERROR, errorType = "SocketTimeoutException")
        )

        assertEquals("error", saved.captured.outcome)
        assertEquals("SocketTimeoutException", saved.captured.errorType)
    }

    @Test
    fun `a repository failure never reaches the caller`() {
        every { repository.save(any<LlmCall>()) } throws
            DataAccessResourceFailureException("database is locked")

        assertDoesNotThrow { service.record(record) }

        verify { repository.save(any<LlmCall>()) }
    }

    @Test
    fun `the episode reaches the row, and its absence is written as no episode`() {
        val saved = slot<LlmCall>()
        every { repository.save(capture(saved)) } answers { saved.captured }

        service.record(record.copy(attribution = LlmCallAttribution(episodeId = 224)))
        assertEquals(224L, saved.captured.episodeId)

        service.record(record)
        assertNull(saved.captured.episodeId)
        assertEquals("compose", saved.captured.stage)
        assertEquals(1500L, saved.captured.durationMs)
    }
}
