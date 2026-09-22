package com.aisummarypodcast.store

import com.aisummarypodcast.llm.LlmCallAttribution
import com.aisummarypodcast.llm.LlmCallLogService
import com.aisummarypodcast.llm.LlmCallRecord
import com.aisummarypodcast.llm.LlmCostSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

/**
 * Covers V76: what a request cost, recorded with the request.
 *
 * The rollback case is the one that matters. Episode 226 paid for a dedup and a dedup-gate call,
 * failed on the save that followed, and reported neither: the cost lived only in the episode
 * columns written inside the transaction that rolled back.
 */
@SpringBootTest
class LlmCallCostMigrationTest {

    @Autowired lateinit var llmCallRepository: LlmCallRepository
    @Autowired lateinit var llmCallLogService: LlmCallLogService
    @Autowired lateinit var transactionTemplate: TransactionTemplate

    @BeforeEach
    fun setUp() {
        llmCallRepository.deleteAll()
    }

    private fun row(resolvedCostUsd: Double?, costSource: String?) = LlmCall(
        startedAt = "2026-09-15T10:00:00Z",
        stage = "dedup",
        provider = "openrouter",
        model = "test-model",
        durationMs = 1234,
        inputTokens = 100,
        outputTokens = 200,
        reportedCostUsd = 0.0042,
        resolvedCostUsd = resolvedCostUsd,
        costSource = costSource,
        cacheHit = false,
        outcome = "ok"
    )

    @Test
    fun `the resolved cost and its source round-trip`() {
        val saved = llmCallRepository.save(row(0.0042, LlmCostSource.API.name))

        val loaded = llmCallRepository.findById(saved.id!!).orElseThrow()
        assertEquals(0.0042, loaded.resolvedCostUsd!!, 1e-12)
        assertEquals(LlmCostSource.API.name, loaded.costSource)
    }

    @Test
    fun `a row written before the columns existed carries neither`() {
        // A cost that was never resolved cannot be reconstructed from a rate table that has since
        // changed, so the migration backfills nothing and null stays the truthful answer.
        val saved = llmCallRepository.save(row(null, null))

        val loaded = llmCallRepository.findById(saved.id!!).orElseThrow()
        assertNull(loaded.resolvedCostUsd)
        assertNull(loaded.costSource)
    }

    @Test
    fun `a request keeps its cost after the stage that issued it rolls back`() {
        transactionTemplate.execute { status ->
            llmCallLogService.record(
                LlmCallRecord(
                    startedAt = Instant.parse("2026-09-15T10:00:00Z"),
                    stage = "dedup",
                    provider = "openrouter",
                    model = "test-model",
                    duration = 1500.milliseconds,
                    inputTokens = 100,
                    outputTokens = 200,
                    reportedCostUsd = 0.0042,
                    attribution = LlmCallAttribution(episodeId = 226)
                )
            )
            status.setRollbackOnly()
        }

        val recorded = llmCallRepository.findAll().toList().single()
        assertEquals(226L, recorded.episodeId)
        assertNotNull(recorded.resolvedCostUsd)
        assertEquals(0.0042, recorded.resolvedCostUsd!!, 1e-12)
        assertEquals(LlmCostSource.API.name, recorded.costSource)
    }
}
