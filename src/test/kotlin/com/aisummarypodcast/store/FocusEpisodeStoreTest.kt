package com.aisummarypodcast.store

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * What the schema and queries guarantee about a focus episode: its research sources round-trip, and
 * it is invisible to the queries that drive the regular schedule and article consumption.
 */
@SpringBootTest
class FocusEpisodeStoreTest {

    @Autowired lateinit var researchSourceRepository: EpisodeResearchSourceRepository
    @Autowired lateinit var episodeRepository: EpisodeRepository
    @Autowired lateinit var episodeArticleRepository: EpisodeArticleRepository
    @Autowired lateinit var articleRepository: ArticleRepository
    @Autowired lateinit var sourceRepository: SourceRepository
    @Autowired lateinit var podcastRepository: PodcastRepository
    @Autowired lateinit var userRepository: UserRepository

    @BeforeEach
    fun setUp() {
        researchSourceRepository.deleteAll()
        episodeArticleRepository.deleteAll()
        episodeRepository.deleteAll()
        articleRepository.deleteAll()
        sourceRepository.deleteAll()
        podcastRepository.deleteAll()
        userRepository.deleteAll()

        userRepository.save(User(id = "u1", name = "Test User"))
        podcastRepository.save(Podcast(id = "p1", userId = "u1", name = "P", topic = "tech"))
        sourceRepository.save(Source(id = "s1", podcastId = "p1", type = SourceType.RSS, url = "https://example.com/feed"))
    }

    private fun episode(focus: String?, windowEnd: String, generatedAt: String = windowEnd) = episodeRepository.save(
        Episode(
            podcastId = "p1", generatedAt = generatedAt, windowStart = "2026-09-01T00:00:00Z", windowEnd = windowEnd,
            scriptText = "script", status = EpisodeStatus.GENERATED, focus = focus
        )
    )

    @Test
    fun `research sources round-trip in recorded order`() {
        val episodeId = episode("Claude Opus 5.5 release", "2026-09-22T06:00:00Z").id!!
        researchSourceRepository.save(EpisodeResearchSource(episodeId = episodeId, query = "q", title = "Second", url = "https://b", ordinal = 1))
        researchSourceRepository.save(EpisodeResearchSource(episodeId = episodeId, query = "q", title = "First", url = "https://a", ordinal = 0))

        val sources = researchSourceRepository.findByEpisodeIdOrderByOrdinal(episodeId)

        assertEquals(listOf("First", "Second"), sources.map { it.title })

        researchSourceRepository.deleteByEpisodeId(episodeId)
        assertTrue(researchSourceRepository.findByEpisodeIdOrderByOrdinal(episodeId).isEmpty())
    }

    @Test
    fun `a focus episode does not count as covered window`() {
        episode(null, "2026-09-21T06:00:00Z")
        episode("Claude Opus 5.5 release", "2026-09-22T05:00:00Z")

        val coveredUntil = episodeRepository.findLatestCoveredWindowEnd("p1", "2026-09-22T06:00:00Z")

        assertEquals("2026-09-21T06:00:00Z", coveredUntil)
    }

    @Test
    fun `a focus episode is left out of the dedup history`() {
        episode("Claude Opus 5.5 release", "2026-09-22T05:00:00Z")

        assertTrue(episodeRepository.findRecentGeneratedByPodcastId("p1", 7).isEmpty())
        assertNull(episodeRepository.findLatestCoveredWindowEnd("p1", "2026-09-23T00:00:00Z"))
    }

    @Test
    fun `articles a focus episode used stay selectable for the next regular episode`() {
        val article = articleRepository.save(
            Article(
                sourceId = "s1", title = "Opus 5.5 ships", body = "body", url = "https://example.com/opus",
                publishedAt = "2026-09-22T01:00:00Z", contentHash = "h1", relevanceScore = 8, isProcessed = false
            )
        )
        val focusEpisode = episode("Claude Opus 5.5 release", "2026-09-22T05:00:00Z")
        episodeArticleRepository.insertIgnore(focusEpisode.id!!, article.id!!, null, null, null)

        val candidates = articleRepository.findRelevantUnprocessedBySourceIds(listOf("s1"), 5)

        assertEquals(listOf(article.id), candidates.map { it.id })
    }
}
