package com.aisummarypodcast.store

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessException

/**
 * Covers what an episode records about the articles it scored: the outcomes it tells apart, the
 * one-episode rule the unique constraint holds, and the requests those articles reach through.
 */
@SpringBootTest
class EpisodeCandidateArticleTest {

    @Autowired lateinit var repository: EpisodeCandidateArticleRepository
    @Autowired lateinit var llmCallRepository: LlmCallRepository

    @BeforeEach
    fun setUp() {
        repository.deleteAll()
        llmCallRepository.deleteAll()
    }

    private fun candidate(episodeId: Long, articleId: Long, outcome: CandidateOutcome) =
        repository.save(EpisodeCandidateArticle(episodeId = episodeId, articleId = articleId, outcome = outcome))

    @Test
    fun `a gate exclusion is distinguishable from a duplicate`() {
        candidate(1L, 10L, CandidateOutcome.USED)
        candidate(1L, 11L, CandidateOutcome.EXCLUDED_BY_GATE)
        candidate(1L, 12L, CandidateOutcome.DROPPED_AS_DUPLICATE)
        candidate(1L, 13L, CandidateOutcome.CUT_BY_COMPOSE_CAP)

        val byOutcome = repository.findByEpisodeId(1L).groupBy { it.outcome }

        assertEquals(4, byOutcome.size)
        assertEquals(11L, byOutcome[CandidateOutcome.EXCLUDED_BY_GATE]!!.single().articleId)
        assertEquals(12L, byOutcome[CandidateOutcome.DROPPED_AS_DUPLICATE]!!.single().articleId)
    }

    @Test
    fun `an article stands as a candidate for one episode only`() {
        candidate(1L, 10L, CandidateOutcome.USED)

        assertThrows(DataAccessException::class.java) {
            candidate(1L, 10L, CandidateOutcome.DROPPED_AS_DUPLICATE)
        }
    }

    @Test
    fun `the same article may be a candidate for a different episode`() {
        candidate(1L, 10L, CandidateOutcome.USED)
        candidate(2L, 10L, CandidateOutcome.USED)

        assertEquals(1, repository.findByEpisodeId(1L).size)
        assertEquals(1, repository.findByEpisodeId(2L).size)
    }

    @Test
    fun `an episode's requests include the scoring calls of its dropped candidates`() {
        candidate(1L, 10L, CandidateOutcome.USED)
        candidate(1L, 11L, CandidateOutcome.DROPPED_AS_DUPLICATE)
        scoringCall(articleId = 10L)
        scoringCall(articleId = 11L)
        // Another episode's candidate, which must not be claimed by episode 1.
        candidate(2L, 12L, CandidateOutcome.USED)
        scoringCall(articleId = 12L)
        composeCall(episodeId = 1L)

        val requests = llmCallRepository.requestsForEpisode(1L)

        assertEquals(3, requests.size)
        assertEquals(2, requests.count { it.stage == "score" })
        assertTrue(requests.any { it.stage == "compose" })
    }

    private fun scoringCall(articleId: Long) = llmCallRepository.save(
        llmCall(stage = "score", articleId = articleId)
    )

    private fun composeCall(episodeId: Long) = llmCallRepository.save(
        llmCall(stage = "compose", episodeId = episodeId)
    )

    private fun llmCall(stage: String, episodeId: Long? = null, articleId: Long? = null) = LlmCall(
        startedAt = "2026-09-15T10:00:00Z",
        stage = stage,
        provider = "openrouter",
        model = "test-model",
        durationMs = 100,
        inputTokens = 10,
        outputTokens = 20,
        cacheHit = false,
        outcome = "ok",
        episodeId = episodeId,
        articleId = articleId
    )
}
