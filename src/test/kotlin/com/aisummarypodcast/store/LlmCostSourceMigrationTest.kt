package com.aisummarypodcast.store

import com.aisummarypodcast.llm.LlmCostSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Covers V64: the reported-cost columns on `llm_cache`, `articles` and `episodes`, plus the cost
 * source on `episodes`. Rows written without the new values (the shape of every row that existed
 * before the migration) must keep null in them and keep their existing costs readable.
 */
@SpringBootTest
class LlmCostSourceMigrationTest {

    @Autowired lateinit var articleRepository: ArticleRepository
    @Autowired lateinit var sourceRepository: SourceRepository
    @Autowired lateinit var podcastRepository: PodcastRepository
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var episodeRepository: EpisodeRepository
    @Autowired lateinit var llmCacheRepository: LlmCacheRepository

    @BeforeEach
    fun setUp() {
        articleRepository.deleteAll()
        sourceRepository.deleteAll()
        podcastRepository.deleteAll()
        userRepository.deleteAll()
        llmCacheRepository.deleteAll()

        userRepository.save(User(id = "u1", name = "Test User"))
        podcastRepository.save(Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech"))
        sourceRepository.save(Source(id = "s1", podcastId = "p1", type = SourceType.RSS, url = "https://example.com/feed"))
    }

    @Test
    fun `episode cost source round-trips and pre-migration rows read as null`() {
        val withSource = episodeRepository.save(
            Episode(
                podcastId = "p1", generatedAt = "2026-08-13T00:00:00Z", scriptText = "",
                scoreCostCents = 3, llmCostSource = LlmCostSource.MIXED
            )
        )
        val legacy = episodeRepository.save(
            Episode(podcastId = "p1", generatedAt = "2026-08-13T00:00:00Z", scriptText = "", scoreCostCents = 3)
        )

        assertEquals(LlmCostSource.MIXED, episodeRepository.findById(withSource.id!!).orElseThrow().llmCostSource)
        val loadedLegacy = episodeRepository.findById(legacy.id!!).orElseThrow()
        assertNull(loadedLegacy.llmCostSource)
        assertEquals(3, loadedLegacy.scoreCostCents)
    }

    @Test
    fun `per-stage reported cost cents round-trip and are null when nothing was reported`() {
        val reported = episodeRepository.save(
            Episode(
                podcastId = "p1", generatedAt = "2026-08-13T00:00:00Z", scriptText = "",
                scoreCostCents = 3, dedupCostCents = 5, composeCostCents = 10, recapCostCents = 1,
                llmCostSource = LlmCostSource.API,
                scoreReportedCostCents = 0.0076,
                dedupReportedCostCents = 4.62,
                composeReportedCostCents = 9.81,
                recapReportedCostCents = 0.5
            )
        )
        val silent = episodeRepository.save(
            Episode(
                podcastId = "p1", generatedAt = "2026-08-13T00:00:00Z", scriptText = "",
                scoreCostCents = 3, llmCostSource = LlmCostSource.TABLE
            )
        )

        val loadedReported = episodeRepository.findById(reported.id!!).orElseThrow()
        assertEquals(0.0076, loadedReported.scoreReportedCostCents)
        assertEquals(4.62, loadedReported.dedupReportedCostCents)
        assertEquals(9.81, loadedReported.composeReportedCostCents)
        assertEquals(0.5, loadedReported.recapReportedCostCents)

        val loadedSilent = episodeRepository.findById(silent.id!!).orElseThrow()
        assertNull(loadedSilent.scoreReportedCostCents)
        assertNull(loadedSilent.dedupReportedCostCents)
        assertNull(loadedSilent.composeReportedCostCents)
        assertNull(loadedSilent.recapReportedCostCents)
        assertEquals(3, loadedSilent.scoreCostCents)
    }

    @Test
    fun `article reported cost round-trips and is null when none was reported`() {
        val reported = articleRepository.save(
            Article(sourceId = "s1", title = "T", body = "b", url = "https://example.com/1",
                contentHash = "h1", llmCostCents = 0, llmReportedCostUsd = 7.6E-5)
        )
        val silent = articleRepository.save(
            Article(sourceId = "s1", title = "T2", body = "b", url = "https://example.com/2",
                contentHash = "h2", llmCostCents = 1)
        )

        assertEquals(7.6E-5, articleRepository.findById(reported.id!!).orElseThrow().llmReportedCostUsd)
        val loadedSilent = articleRepository.findById(silent.id!!).orElseThrow()
        assertNull(loadedSilent.llmReportedCostUsd)
        assertEquals(1, loadedSilent.llmCostCents)
    }

    @Test
    fun `cache reported cost round-trips and is null for legacy rows`() {
        val reported = llmCacheRepository.save(
            LlmCache(promptHash = "h1", model = "m", response = "r", createdAt = "now",
                inputTokens = 10, outputTokens = 5, reportedCostUsd = 0.00042)
        )
        val legacy = llmCacheRepository.save(
            LlmCache(promptHash = "h2", model = "m", response = "r", createdAt = "now",
                inputTokens = 10, outputTokens = 5)
        )

        assertEquals(0.00042, llmCacheRepository.findById(reported.id!!).orElseThrow().reportedCostUsd)
        val loadedLegacy = llmCacheRepository.findById(legacy.id!!).orElseThrow()
        assertNull(loadedLegacy.reportedCostUsd)
        assertEquals(10, loadedLegacy.inputTokens)
    }
}
