package com.aisummarypodcast.store

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * The episode queries that replaced in-memory filtering: each has to keep the exclusions
 * (other podcast, other status, experiment purpose) and the ordering the service relied on.
 */
@SpringBootTest
class EpisodeQueryRepositoryTest {

    @Autowired lateinit var episodeRepository: EpisodeRepository
    @Autowired lateinit var episodeArticleRepository: EpisodeArticleRepository
    @Autowired lateinit var articleRepository: ArticleRepository
    @Autowired lateinit var sourceRepository: SourceRepository
    @Autowired lateinit var podcastRepository: PodcastRepository
    @Autowired lateinit var userRepository: UserRepository

    private val inFlight = listOf(
        EpisodeStatus.GENERATING, EpisodeStatus.PENDING_REVIEW, EpisodeStatus.APPROVED, EpisodeStatus.GENERATING_AUDIO
    )

    @BeforeEach
    fun setUp() {
        episodeArticleRepository.deleteAll()
        episodeRepository.deleteAll()
        articleRepository.deleteAll()
        sourceRepository.deleteAll()
        podcastRepository.deleteAll()
        userRepository.deleteAll()

        userRepository.save(User(id = "u1", name = "U"))
        podcastRepository.save(Podcast(id = "p1", userId = "u1", name = "P", topic = "tech"))
        podcastRepository.save(Podcast(id = "p2", userId = "u1", name = "P2", topic = "tech"))
        sourceRepository.save(Source(id = "s1", podcastId = "p1", type = SourceType.RSS, url = "https://example.com/feed"))
    }

    private fun episode(
        podcastId: String = "p1",
        generatedAt: String = "2026-09-20T06:00:00Z",
        status: EpisodeStatus = EpisodeStatus.GENERATED,
        purpose: EpisodePurpose = EpisodePurpose.LEGACY,
        focus: String? = null
    ) = episodeRepository.save(
        Episode(podcastId = podcastId, generatedAt = generatedAt, scriptText = "s", status = status, purpose = purpose, focus = focus)
    )

    // --- cleanup ---

    @Test
    fun `findByPodcastIdAndGeneratedAtLessThan returns only the podcast's episodes before the cutoff`() {
        val old = episode(generatedAt = "2026-08-01T06:00:00Z")
        episode(generatedAt = "2026-09-20T06:00:00Z")
        episode(generatedAt = "2026-09-01T00:00:00Z") // exactly the cutoff, not before it
        episode(podcastId = "p2", generatedAt = "2026-08-01T06:00:00Z")

        val found = episodeRepository.findByPodcastIdAndGeneratedAtLessThan("p1", "2026-09-01T00:00:00Z")

        assertEquals(listOf(old.id), found.map { it.id })
    }

    // --- active episode ---

    @Test
    fun `an in-flight regular episode counts as regular and not as focus`() {
        episode(status = EpisodeStatus.PENDING_REVIEW)

        assertTrue(episodeRepository.existsByPodcastIdAndStatusInAndPurposeNotAndFocusIsNull("p1", inFlight, EpisodePurpose.EXPERIMENT))
        assertFalse(episodeRepository.existsByPodcastIdAndStatusInAndPurposeNotAndFocusIsNotNull("p1", inFlight, EpisodePurpose.EXPERIMENT))
    }

    @Test
    fun `an in-flight focus episode counts as focus and not as regular`() {
        episode(status = EpisodeStatus.GENERATING_AUDIO, focus = "Opus release")

        assertFalse(episodeRepository.existsByPodcastIdAndStatusInAndPurposeNotAndFocusIsNull("p1", inFlight, EpisodePurpose.EXPERIMENT))
        assertTrue(episodeRepository.existsByPodcastIdAndStatusInAndPurposeNotAndFocusIsNotNull("p1", inFlight, EpisodePurpose.EXPERIMENT))
    }

    @Test
    fun `the active check ignores other podcasts, finished statuses and experiments`() {
        episode(podcastId = "p2", status = EpisodeStatus.PENDING_REVIEW)
        episode(status = EpisodeStatus.GENERATED)
        episode(status = EpisodeStatus.FAILED)
        episode(status = EpisodeStatus.PENDING_REVIEW, purpose = EpisodePurpose.EXPERIMENT)
        episode(status = EpisodeStatus.PENDING_REVIEW, purpose = EpisodePurpose.EXPERIMENT, focus = "experiment focus")

        assertFalse(episodeRepository.existsByPodcastIdAndStatusInAndPurposeNotAndFocusIsNull("p1", inFlight, EpisodePurpose.EXPERIMENT))
        assertFalse(episodeRepository.existsByPodcastIdAndStatusInAndPurposeNotAndFocusIsNotNull("p1", inFlight, EpisodePurpose.EXPERIMENT))
    }

    // --- recent focus episodes ---

    @Test
    fun `focus episodes are returned oldest first and only GENERATED, non-experiment, in the podcast`() {
        val later = episode(generatedAt = "2026-09-22T06:00:00Z", focus = "later")
        val earlier = episode(generatedAt = "2026-09-21T06:00:00Z", focus = "earlier")
        episode(generatedAt = "2026-09-21T07:00:00Z") // regular, no focus
        episode(generatedAt = "2026-09-21T08:00:00Z", status = EpisodeStatus.PENDING_REVIEW, focus = "pending")
        episode(generatedAt = "2026-09-21T09:00:00Z", purpose = EpisodePurpose.EXPERIMENT, focus = "experiment")
        episode(podcastId = "p2", generatedAt = "2026-09-21T10:00:00Z", focus = "other podcast")

        val found = episodeRepository.findByPodcastIdAndStatusAndPurposeNotAndFocusIsNotNullOrderByGeneratedAtAsc(
            "p1", EpisodeStatus.GENERATED, EpisodePurpose.EXPERIMENT
        )

        assertEquals(listOf(earlier.id, later.id), found.map { it.id })
    }

    @Test
    fun `focus episodes after a time exclude the one at that time and older ones`() {
        episode(generatedAt = "2026-09-19T06:00:00Z", focus = "older")
        episode(generatedAt = "2026-09-20T06:00:00Z", focus = "same instant")
        val newer = episode(generatedAt = "2026-09-21T06:00:00Z", focus = "newer")

        val found = episodeRepository.findByPodcastIdAndStatusAndPurposeNotAndFocusIsNotNullAndGeneratedAtGreaterThanOrderByGeneratedAtAsc(
            "p1", EpisodeStatus.GENERATED, EpisodePurpose.EXPERIMENT, "2026-09-20T06:00:00Z"
        )

        assertEquals(listOf(newer.id), found.map { it.id })
    }

    // --- linked articles ---

    @Test
    fun `countByEpisodeId counts only the episode's links`() {
        val e1 = episode()
        val e2 = episode()
        val a1 = articleRepository.save(Article(sourceId = "s1", title = "A1", body = "b", url = "https://example.com/1", contentHash = "h1"))
        val a2 = articleRepository.save(Article(sourceId = "s1", title = "A2", body = "b", url = "https://example.com/2", contentHash = "h2"))
        episodeArticleRepository.save(EpisodeArticle(episodeId = e1.id!!, articleId = a1.id!!))
        episodeArticleRepository.save(EpisodeArticle(episodeId = e1.id!!, articleId = a2.id!!))
        episodeArticleRepository.save(EpisodeArticle(episodeId = e2.id!!, articleId = a1.id!!))

        assertEquals(2, episodeArticleRepository.countByEpisodeId(e1.id!!))
        assertEquals(1, episodeArticleRepository.countByEpisodeId(e2.id!!))
        assertEquals(0, episodeArticleRepository.countByEpisodeId(-1L))
    }
}
