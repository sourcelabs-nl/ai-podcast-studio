package com.aisummarypodcast.store

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Covers V69: the conditions an evaluation run was composed under.
 *
 * The cascade to `episodes` is not exercised here. The test datasource does not set
 * `PRAGMA foreign_keys = ON` the way `application.yaml` does for the running application, so SQLite
 * would not enforce it and the assertion would describe the harness rather than the schema.
 */
@SpringBootTest
class EvaluationRunMigrationTest {

    @Autowired lateinit var evaluationRunRepository: EvaluationRunRepository
    @Autowired lateinit var episodeRepository: EpisodeRepository
    @Autowired lateinit var podcastRepository: PodcastRepository
    @Autowired lateinit var userRepository: UserRepository

    @BeforeEach
    fun setUp() {
        evaluationRunRepository.deleteAll()
        episodeRepository.deleteAll()
        podcastRepository.deleteAll()
        userRepository.deleteAll()

        userRepository.save(User(id = "u1", name = "Test User"))
        podcastRepository.save(Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech"))
    }

    private fun episode() = episodeRepository.save(
        Episode(podcastId = "p1", generatedAt = "2026-09-15T00:00:00Z", scriptText = "script")
    )

    private fun run(episodeId: Long, promptHash: String = "hash1") = evaluationRunRepository.save(
        EvaluationRun(
            episodeId = episodeId,
            podcastId = "p1",
            ranAt = "2026-09-15T10:00:00Z",
            promptHash = promptHash,
            varietySelection = "openingStyle=SCENE_SET",
            composeModel = "test-model",
            temperature = 0.8,
            cacheBypassed = true,
            cacheHit = false,
            toolsFiredJson = """{"searchPastEpisodes":2}"""
        )
    )

    @Test
    fun `a run round-trips every condition it was composed under`() {
        val saved = run(episode().id!!)

        val loaded = evaluationRunRepository.findById(saved.id!!).orElseThrow()
        assertEquals("hash1", loaded.promptHash)
        assertEquals("openingStyle=SCENE_SET", loaded.varietySelection)
        assertEquals("test-model", loaded.composeModel)
        assertEquals(0.8, loaded.temperature)
        assertTrue(loaded.cacheBypassed)
        assertEquals(false, loaded.cacheHit)
        assertEquals("""{"searchPastEpisodes":2}""", loaded.toolsFiredJson)
    }

    @Test
    fun `runs for a podcast come back newest first`() {
        val episodeId = episode().id!!
        run(episodeId, promptHash = "older")
        run(episodeId, promptHash = "newer")

        val runs = evaluationRunRepository.findTop200ByPodcastIdOrderByIdDesc("p1")

        assertEquals(listOf("newer", "older"), runs.map { it.promptHash })
    }
}
