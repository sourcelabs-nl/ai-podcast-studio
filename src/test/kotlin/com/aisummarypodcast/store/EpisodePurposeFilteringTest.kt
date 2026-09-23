package com.aisummarypodcast.store

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest

/**
 * Every episode query an experiment must not reach: a newer EXPERIMENT episode is ignored while the
 * older regular one is found.
 */
@SpringBootTest
class EpisodePurposeFilteringTest {

    @Autowired lateinit var episodeRepository: EpisodeRepository
    @Autowired lateinit var publicationRepository: EpisodePublicationRepository
    @Autowired lateinit var podcastRepository: PodcastRepository
    @Autowired lateinit var userRepository: UserRepository

    private lateinit var regular: Episode
    private lateinit var experiment: Episode

    @BeforeEach
    fun setUp() {
        publicationRepository.deleteAll()
        episodeRepository.deleteAll()
        podcastRepository.deleteAll()
        userRepository.deleteAll()
        userRepository.save(User(id = "u1", name = "U"))
        podcastRepository.save(Podcast(id = "p1", userId = "u1", name = "P", topic = "tech"))

        regular = episodeRepository.save(episode("2026-09-01T10:00:00Z", "2026-09-01T00:00:00Z", EpisodePurpose.SCHEDULED))
        experiment = episodeRepository.save(episode("2026-09-02T10:00:00Z", "2026-09-02T00:00:00Z", EpisodePurpose.EXPERIMENT))
    }

    private fun episode(generatedAt: String, windowEnd: String, purpose: EpisodePurpose) = Episode(
        podcastId = "p1",
        generatedAt = generatedAt,
        windowStart = "2026-08-31T00:00:00Z",
        windowEnd = windowEnd,
        scriptText = "script",
        status = EpisodeStatus.GENERATED,
        purpose = purpose
    )

    @Test
    fun `a newly saved episode without a purpose is LEGACY`() {
        val saved = episodeRepository.save(Episode(podcastId = "p1", generatedAt = "2026-09-03T00:00:00Z", scriptText = ""))

        assertEquals(EpisodePurpose.LEGACY, episodeRepository.findById(saved.id!!).get().purpose)
    }

    @Test
    fun `schedule resolution ignores an experiment episode`() {
        assertEquals(regular.id, episodeRepository.findLatestCoveringByPodcastId("p1")?.id)
        assertEquals("2026-09-01T00:00:00Z", episodeRepository.findLatestCoveredWindowEnd("p1", "2026-09-10T00:00:00Z"))
    }

    @Test
    fun `dedup history ignores an experiment episode`() {
        assertEquals(listOf(regular.id), episodeRepository.findRecentGeneratedByPodcastId("p1", 10).map { it.id })
    }

    @Test
    fun `episode lists and the feed query ignore an experiment episode`() {
        val purpose = EpisodePurpose.EXPERIMENT
        assertEquals(listOf(regular.id), episodeRepository.findByPodcastIdAndPurposeNotOrderByGeneratedAtDescIdDesc("p1", purpose).map { it.id })
        assertEquals(
            listOf(regular.id),
            episodeRepository.findByPodcastIdAndStatusAndPurposeNotOrderByGeneratedAtDescIdDesc("p1", EpisodeStatus.GENERATED, purpose).map { it.id }
        )
        assertEquals(listOf(regular.id), episodeRepository.findByPodcastIdAndPurposeNot("p1", purpose, PageRequest.of(0, 10)).content.map { it.id })
        assertEquals(
            listOf(regular.id),
            episodeRepository.findByPodcastIdAndStatusInAndPurposeNot("p1", listOf(EpisodeStatus.GENERATED), purpose, PageRequest.of(0, 10)).content.map { it.id }
        )
    }

    @Test
    fun `the active-episode lookup ignores an experiment episode`() {
        episodeRepository.save(experiment.copy(status = EpisodeStatus.GENERATING))

        assertTrue(
            episodeRepository.findByPodcastIdAndStatusInAndPurposeNot("p1", listOf(EpisodeStatus.GENERATING), EpisodePurpose.EXPERIMENT).isEmpty()
        )
    }
}
