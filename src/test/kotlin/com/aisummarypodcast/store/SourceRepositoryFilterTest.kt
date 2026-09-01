package com.aisummarypodcast.store

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * The Sources tab narrows by `enabled` in the query rather than on the result, so the derived
 * finder has to actually work against SQLite and stay scoped to the one podcast.
 */
@SpringBootTest
class SourceRepositoryFilterTest {

    @Autowired lateinit var sourceRepository: SourceRepository
    @Autowired lateinit var podcastRepository: PodcastRepository
    @Autowired lateinit var userRepository: UserRepository

    @BeforeEach
    fun setUp() {
        sourceRepository.deleteAll()
        podcastRepository.deleteAll()
        userRepository.deleteAll()

        userRepository.save(User(id = "u1", name = "Test User"))
        podcastRepository.save(Podcast(id = "p1", userId = "u1", name = "One", topic = "tech"))
        podcastRepository.save(Podcast(id = "p2", userId = "u1", name = "Two", topic = "tech"))

        fun source(id: String, podcastId: String, enabled: Boolean) = sourceRepository.save(
            Source(
                id = id, podcastId = podcastId, type = SourceType.RSS,
                url = "https://example.com/$id", enabled = enabled
            )
        )
        source("live-1", "p1", true)
        source("live-2", "p1", true)
        source("retired-1", "p1", false)
        source("retired-2", "p1", false)
        source("retired-3", "p1", false)
        source("other-podcast", "p2", true)
    }

    @Test
    fun `enabled true returns only the podcast's live sources`() {
        val found = sourceRepository.findByPodcastIdAndEnabled("p1", true)

        assertEquals(listOf("live-1", "live-2"), found.map { it.id }.sorted())
    }

    @Test
    fun `enabled false returns only the podcast's retired sources`() {
        val found = sourceRepository.findByPodcastIdAndEnabled("p1", false)

        assertEquals(listOf("retired-1", "retired-2", "retired-3"), found.map { it.id }.sorted())
    }

    @Test
    fun `the filter stays scoped to one podcast`() {
        val found = sourceRepository.findByPodcastIdAndEnabled("p2", true)

        assertEquals(listOf("other-podcast"), found.map { it.id })
    }

    @Test
    fun `the unfiltered finder still returns both`() {
        assertEquals(5, sourceRepository.findByPodcastId("p1").size)
    }
}
