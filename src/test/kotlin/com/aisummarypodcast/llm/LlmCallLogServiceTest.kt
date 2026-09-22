package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.ModelCost
import com.aisummarypodcast.config.ModelType
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

    // Only the rate table is read, and building the whole property tree to supply it would say
    // nothing about what is under test.
    private val appProperties = mockk<AppProperties> {
        every { models } returns mapOf(
            "openrouter" to mapOf(
                "test-model" to ModelCost(
                    type = ModelType.LLM,
                    inputCostPerMtok = 1.0,
                    outputCostPerMtok = 2.0
                ),
                "rateless-model" to ModelCost(type = ModelType.LLM)
            )
        )
    }
    private val service = LlmCallLogService(repository, appProperties)

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
    fun `a provider-reported cost is recorded as reported`() {
        val saved = slot<LlmCall>()
        every { repository.save(capture(saved)) } answers { saved.captured }

        service.record(record)

        assertEquals(0.0042, saved.captured.resolvedCostUsd!!, 1e-12)
        assertEquals(LlmCostSource.API.name, saved.captured.costSource)
    }

    @Test
    fun `a call that reports nothing is costed from the configured rates`() {
        val saved = slot<LlmCall>()
        every { repository.save(capture(saved)) } answers { saved.captured }

        service.record(record.copy(reportedCostUsd = null))

        // 100 input at $1/Mtok plus 200 output at $2/Mtok.
        assertEquals(0.0005, saved.captured.resolvedCostUsd!!, 1e-12)
        assertEquals(LlmCostSource.TABLE.name, saved.captured.costSource)
    }

    @Test
    fun `a call with neither a reported cost nor a rate carries no cost`() {
        val saved = slot<LlmCall>()
        every { repository.save(capture(saved)) } answers { saved.captured }

        // Not zero: a zero is indistinguishable from a call that genuinely cost nothing.
        service.record(record.copy(reportedCostUsd = null, model = "rateless-model"))

        assertNull(saved.captured.resolvedCostUsd)
        assertEquals(LlmCostSource.UNKNOWN.name, saved.captured.costSource)
    }

    @Test
    fun `a cost replayed from the cache is marked as such`() {
        val saved = slot<LlmCall>()
        every { repository.save(capture(saved)) } answers { saved.captured }

        service.record(record.copy(cacheHit = true))

        assertEquals(0.0042, saved.captured.resolvedCostUsd!!, 1e-12)
        assertEquals(LlmCostSource.API_CACHED.name, saved.captured.costSource)
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
        // It was not charged, so the row says so however it was called: the record itself has to be
        // truthful, not only the query that sums it.
        assertNull(saved.captured.resolvedCostUsd)
        assertEquals(LlmCostSource.UNKNOWN.name, saved.captured.costSource)
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
