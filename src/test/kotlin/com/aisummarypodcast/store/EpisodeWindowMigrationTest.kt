package com.aisummarypodcast.store

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Covers V67: the article window on `episodes`, and the query that finds where a podcast's coverage
 * ends. Episodes written before the migration keep null in both columns and must not be treated as
 * having covered anything.
 */
@SpringBootTest
class EpisodeWindowMigrationTest {

    @Autowired lateinit var episodeRepository: EpisodeRepository
    @Autowired lateinit var podcastRepository: PodcastRepository
    @Autowired lateinit var userRepository: UserRepository

    @BeforeEach
    fun setUp() {
        episodeRepository.deleteAll()
        podcastRepository.deleteAll()
        userRepository.deleteAll()

        userRepository.save(User(id = "u1", name = "Test User"))
        podcastRepository.save(Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech"))
    }

    private fun episode(
        windowStart: String? = null,
        windowEnd: String? = null,
        status: EpisodeStatus = EpisodeStatus.GENERATED
    ) = episodeRepository.save(
        Episode(
            podcastId = "p1",
            generatedAt = windowEnd ?: "2026-09-08T13:00:00Z",
            windowStart = windowStart,
            windowEnd = windowEnd,
            scriptText = "script",
            status = status
        )
    )

    @Test
    fun `the window round-trips and pre-migration rows read as null`() {
        val windowed = episode(windowStart = "2026-09-07T13:00:00Z", windowEnd = "2026-09-08T13:00:00Z")
        val legacy = episode()

        val loaded = episodeRepository.findById(windowed.id!!).orElseThrow()
        assertEquals("2026-09-07T13:00:00Z", loaded.windowStart)
        assertEquals("2026-09-08T13:00:00Z", loaded.windowEnd)

        val loadedLegacy = episodeRepository.findById(legacy.id!!).orElseThrow()
        assertNull(loadedLegacy.windowStart)
        assertNull(loadedLegacy.windowEnd)
    }

    @Test
    fun `coverage ends at the latest window of an episode that was not failed or discarded`() {
        episode(windowStart = "2026-09-06T13:00:00Z", windowEnd = "2026-09-07T13:00:00Z")
        episode(windowStart = "2026-09-07T13:00:00Z", windowEnd = "2026-09-08T13:00:00Z")

        val covered = episodeRepository.findLatestCoveredWindowEnd("p1", "2026-09-09T13:00:00Z")

        assertEquals("2026-09-08T13:00:00Z", covered)
    }

    @Test
    fun `a failed or discarded episode covered nothing`() {
        episode(windowStart = "2026-09-06T13:00:00Z", windowEnd = "2026-09-07T13:00:00Z")
        episode(windowStart = "2026-09-07T13:00:00Z", windowEnd = "2026-09-08T13:00:00Z", status = EpisodeStatus.FAILED)
        episode(windowStart = "2026-09-08T13:00:00Z", windowEnd = "2026-09-09T13:00:00Z", status = EpisodeStatus.DISCARDED)

        val covered = episodeRepository.findLatestCoveredWindowEnd("p1", "2026-09-10T13:00:00Z")

        assertEquals("2026-09-07T13:00:00Z", covered)
    }

    @Test
    fun `coverage ignores windows ending after the one being resolved`() {
        episode(windowStart = "2026-09-06T13:00:00Z", windowEnd = "2026-09-07T13:00:00Z")
        episode(windowStart = "2026-09-09T13:00:00Z", windowEnd = "2026-09-10T13:00:00Z")

        val covered = episodeRepository.findLatestCoveredWindowEnd("p1", "2026-09-08T13:00:00Z")

        assertEquals("2026-09-07T13:00:00Z", covered)
    }

    @Test
    fun `coverage is null when no episode carries a window`() {
        episode()

        assertNull(episodeRepository.findLatestCoveredWindowEnd("p1", "2026-09-09T13:00:00Z"))
    }
}
