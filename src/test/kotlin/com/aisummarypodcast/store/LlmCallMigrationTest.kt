package com.aisummarypodcast.store

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/** Covers V70: one row per LLM request, with its duration and outcome. */
@SpringBootTest
class LlmCallMigrationTest {

    @Autowired lateinit var llmCallRepository: LlmCallRepository

    @BeforeEach
    fun setUp() {
        llmCallRepository.deleteAll()
    }

    private fun call(
        startedAt: String = "2026-09-15T10:00:00Z",
        reportedCostUsd: Double? = 0.0042,
        errorType: String? = null,
        outcome: String = "ok"
    ) = llmCallRepository.save(
        LlmCall(
            startedAt = startedAt,
            stage = "compose",
            provider = "openrouter",
            model = "test-model",
            durationMs = 1234,
            inputTokens = 100,
            outputTokens = 200,
            reportedCostUsd = reportedCostUsd,
            cacheHit = false,
            outcome = outcome,
            errorType = errorType
        )
    )

    @Test
    fun `a call round-trips every column`() {
        val saved = call()

        val loaded = llmCallRepository.findById(saved.id!!).orElseThrow()
        assertEquals("2026-09-15T10:00:00Z", loaded.startedAt)
        assertEquals("compose", loaded.stage)
        assertEquals("openrouter", loaded.provider)
        assertEquals("test-model", loaded.model)
        assertEquals(1234L, loaded.durationMs)
        assertEquals(100, loaded.inputTokens)
        assertEquals(200, loaded.outputTokens)
        assertEquals(0.0042, loaded.reportedCostUsd)
        assertEquals(false, loaded.cacheHit)
        assertEquals("ok", loaded.outcome)
        assertNull(loaded.errorType)
    }

    @Test
    fun `cost and error type are optional`() {
        val saved = call(reportedCostUsd = null, outcome = "error", errorType = "SocketTimeoutException")

        val loaded = llmCallRepository.findById(saved.id!!).orElseThrow()
        assertNull(loaded.reportedCostUsd)
        assertEquals("error", loaded.outcome)
        assertEquals("SocketTimeoutException", loaded.errorType)
    }

    @Test
    fun `retention deletes only rows older than the cutoff`() {
        call(startedAt = "2026-09-01T00:00:00Z")
        val kept = call(startedAt = "2026-09-14T00:00:00Z")

        llmCallRepository.deleteByStartedAtLessThan("2026-09-10T00:00:00Z")

        val remaining = llmCallRepository.findAll().toList()
        assertEquals(1, remaining.size)
        assertTrue(remaining.single().id == kept.id)
    }
}
