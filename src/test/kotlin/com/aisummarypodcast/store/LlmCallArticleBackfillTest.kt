package com.aisummarypodcast.store

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Covers the rule V77 backfills historical scoring requests by.
 *
 * The migration itself runs against an empty schema here, so the statement is read back out of the
 * migration file and applied to fixtures. Restating the SQL in the test would let the test pass
 * while the migration is wrong, which is the one thing it exists to prevent.
 */
@SpringBootTest
class LlmCallArticleBackfillTest {

    @Autowired lateinit var jdbcTemplate: JdbcTemplate
    @Autowired lateinit var llmCallRepository: LlmCallRepository
    @Autowired lateinit var articleRepository: ArticleRepository

    private val backfill: String by lazy {
        ClassPathResource("db/migration/V77__backfill_llm_call_article_id.sql")
            .inputStream.reader().readText()
    }

    @BeforeEach
    fun setUp() {
        llmCallRepository.deleteAll()
        articleRepository.deleteAll()
    }

    private fun article(inputTokens: Int, outputTokens: Int, reportedCostUsd: Double?): Long =
        articleRepository.save(
            Article(
                sourceId = "s",
                title = "t",
                body = "b",
                url = "https://example.test/${System.nanoTime()}",
                contentHash = "h${System.nanoTime()}",
                llmInputTokens = inputTokens,
                llmOutputTokens = outputTokens,
                llmReportedCostUsd = reportedCostUsd
            )
        ).id!!

    private fun scoringCall(
        inputTokens: Int,
        outputTokens: Int,
        reportedCostUsd: Double?,
        outcome: String = "ok"
    ): Long = llmCallRepository.save(
        LlmCall(
            startedAt = "2026-09-15T10:00:00Z",
            stage = "filter",
            provider = "openrouter",
            model = "test-model",
            durationMs = 100,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            reportedCostUsd = reportedCostUsd,
            cacheHit = false,
            outcome = outcome
        )
    ).id!!

    private fun articleIdOf(callId: Long): Long? =
        jdbcTemplate.queryForObject("SELECT article_id FROM llm_calls WHERE id = ?", Long::class.javaObjectType, callId)

    @Test
    fun `a request matching exactly one article is linked to it`() {
        val articleId = article(1000, 200, 0.0042)
        val callId = scoringCall(1000, 200, 0.0042)

        jdbcTemplate.execute(backfill)

        assertEquals(articleId, articleIdOf(callId))
    }

    @Test
    fun `a request matching two articles stays unattributed`() {
        article(1000, 200, 0.0042)
        article(1000, 200, 0.0042)
        val callId = scoringCall(1000, 200, 0.0042)

        jdbcTemplate.execute(backfill)

        // A guess here would move real money onto the wrong episode; the gap is the safer answer.
        assertNull(articleIdOf(callId))
    }

    @Test
    fun `two requests matching one article both stay unattributed`() {
        article(1000, 200, 0.0042)
        val first = scoringCall(1000, 200, 0.0042)
        val second = scoringCall(1000, 200, 0.0042)

        jdbcTemplate.execute(backfill)

        assertNull(articleIdOf(first))
        assertNull(articleIdOf(second))
    }

    @Test
    fun `a failed request is not matched`() {
        // It reported no tokens, so it would match every article that also reports none.
        article(0, 0, null)
        val callId = scoringCall(0, 0, null, outcome = "error")

        jdbcTemplate.execute(backfill)

        assertNull(articleIdOf(callId))
    }
}
