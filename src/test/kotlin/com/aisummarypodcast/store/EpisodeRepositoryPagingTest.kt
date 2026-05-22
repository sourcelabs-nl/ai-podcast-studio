package com.aisummarypodcast.store

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootTest
class EpisodeRepositoryPagingTest {

    @Autowired lateinit var episodeRepository: EpisodeRepository
    @Autowired lateinit var publicationRepository: EpisodePublicationRepository
    @Autowired lateinit var podcastRepository: PodcastRepository
    @Autowired lateinit var userRepository: UserRepository

    @BeforeEach
    fun setUp() {
        publicationRepository.deleteAll()
        episodeRepository.deleteAll()
        podcastRepository.deleteAll()
        userRepository.deleteAll()

        userRepository.save(User(id = "u1", name = "U"))
        podcastRepository.save(Podcast(id = "p1", userId = "u1", name = "P", topic = "tech"))
        podcastRepository.save(Podcast(id = "p2", userId = "u1", name = "P2", topic = "tech"))
    }

    private fun episode(podcastId: String, ordinal: Int, status: EpisodeStatus = EpisodeStatus.GENERATED): Episode {
        val ts = Instant.now().minus(ordinal.toLong(), ChronoUnit.MINUTES).toString()
        return Episode(
            podcastId = podcastId,
            generatedAt = ts,
            scriptText = "script $ordinal",
            status = status
        )
    }

    @Test
    fun `findByPodcastId paged returns correct page and total`() {
        repeat(7) { i -> episodeRepository.save(episode("p1", i)) }
        episodeRepository.save(episode("p2", 0)) // sibling podcast, must not bleed

        val page0 = episodeRepository.findByPodcastId(
            "p1",
            PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "generatedAt", "id"))
        )
        assertEquals(3, page0.content.size)
        assertEquals(7L, page0.totalElements)
        assertEquals(3, page0.totalPages)

        val page2 = episodeRepository.findByPodcastId(
            "p1",
            PageRequest.of(2, 3, Sort.by(Sort.Direction.DESC, "generatedAt", "id"))
        )
        assertEquals(1, page2.content.size)
    }

    @Test
    fun `findByPodcastIdAndStatusIn paged filters by multiple statuses`() {
        episodeRepository.save(episode("p1", 0, EpisodeStatus.GENERATED))
        episodeRepository.save(episode("p1", 1, EpisodeStatus.FAILED))
        episodeRepository.save(episode("p1", 2, EpisodeStatus.DISCARDED))
        episodeRepository.save(episode("p1", 3, EpisodeStatus.GENERATED))

        val page = episodeRepository.findByPodcastIdAndStatusIn(
            "p1",
            listOf(EpisodeStatus.GENERATED, EpisodeStatus.FAILED),
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id"))
        )
        assertEquals(3, page.content.size)
        assertEquals(3L, page.totalElements)
        assertTrue(page.content.all { it.status == EpisodeStatus.GENERATED || it.status == EpisodeStatus.FAILED })
    }

    @Test
    fun `publications findByEpisodeIdIn paged scopes to the podcast`() {
        val e1 = episodeRepository.save(episode("p1", 0))
        val e2 = episodeRepository.save(episode("p1", 1))
        val e3 = episodeRepository.save(episode("p2", 0))

        val now = Instant.now()
        val p1Pub = publicationRepository.save(EpisodePublication(episodeId = e1.id!!, target = "rss", status = PublicationStatus.PUBLISHED, createdAt = now.toString()))
        val p2Pub = publicationRepository.save(EpisodePublication(episodeId = e2.id!!, target = "soundcloud", status = PublicationStatus.PUBLISHED, createdAt = now.minusSeconds(60).toString()))
        publicationRepository.save(EpisodePublication(episodeId = e3.id!!, target = "rss", status = PublicationStatus.PUBLISHED, createdAt = now.toString()))

        val episodeIds = setOf(e1.id!!, e2.id!!)
        val page = publicationRepository.findByEpisodeIdIn(
            episodeIds,
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt", "id"))
        )
        assertEquals(2, page.content.size)
        assertEquals(2L, page.totalElements)
        // Sort by createdAt DESC: p1Pub (now) before p2Pub (now-60s)
        assertEquals(p1Pub.id, page.content[0].id)
        assertEquals(p2Pub.id, page.content[1].id)
    }

    @Test
    fun `publications pagination splits results`() {
        val episodes = (0..4).map { episodeRepository.save(episode("p1", it)) }
        val now = Instant.now()
        episodes.forEachIndexed { idx, ep ->
            publicationRepository.save(
                EpisodePublication(
                    episodeId = ep.id!!,
                    target = "rss",
                    status = PublicationStatus.PUBLISHED,
                    createdAt = now.minusSeconds(idx.toLong()).toString()
                )
            )
        }

        val episodeIds = episodes.map { it.id!! }
        val sort = Sort.by(Sort.Direction.DESC, "createdAt", "id")
        val page0 = publicationRepository.findByEpisodeIdIn(episodeIds, PageRequest.of(0, 2, sort))
        val page1 = publicationRepository.findByEpisodeIdIn(episodeIds, PageRequest.of(1, 2, sort))
        val page2 = publicationRepository.findByEpisodeIdIn(episodeIds, PageRequest.of(2, 2, sort))

        assertEquals(2, page0.content.size)
        assertEquals(2, page1.content.size)
        assertEquals(1, page2.content.size)
        assertEquals(5L, page0.totalElements)
        assertEquals(3, page0.totalPages)
    }
}
