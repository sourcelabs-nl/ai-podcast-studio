package com.aisummarypodcast.eval

import com.aisummarypodcast.llm.EvaluationRunProvenance
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EvaluationRun
import com.aisummarypodcast.store.EvaluationRunRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class EvaluationRunRecorderTest {

    private val evaluationRunRepository = mockk<EvaluationRunRepository> {
        every { save(any<EvaluationRun>()) } answers { firstArg<EvaluationRun>().copy(id = 1L) }
    }
    private val recorder = EvaluationRunRecorder(evaluationRunRepository, JsonMapper.builder().build())

    private fun episode(id: Long? = 7L) = Episode(
        id = id, podcastId = "p1", generatedAt = "2026-09-15T00:00:00Z", scriptText = "<expert>Hi.</expert>"
    )

    private fun provenance(cacheHit: Boolean = false) = EvaluationRunProvenance(
        promptHash = "abc123def456",
        varietySelection = "openingStyle=SCENE_SET",
        composeModel = "test-model",
        temperature = 0.8,
        cacheBypassed = true,
        cacheHit = cacheHit,
        toolsFired = mapOf("searchPastEpisodes" to 2)
    )

    @Test
    fun `an ordinary generation records nothing`() {
        assertNull(recorder.record(episode(), provenance = null))
        verify(exactly = 0) { evaluationRunRepository.save(any<EvaluationRun>()) }
    }

    @Test
    fun `an evaluation run records the conditions it ran under`() {
        val saved = slot<EvaluationRun>()

        recorder.record(episode(), provenance())

        verify { evaluationRunRepository.save(capture(saved)) }
        assertEquals(7L, saved.captured.episodeId)
        assertEquals("p1", saved.captured.podcastId)
        assertEquals("abc123def456", saved.captured.promptHash)
        assertEquals("test-model", saved.captured.composeModel)
        assertEquals(0.8, saved.captured.temperature)
        assertTrue(saved.captured.cacheBypassed)
        assertEquals("""{"searchPastEpisodes":2}""", saved.captured.toolsFiredJson)
    }

    @Test
    fun `the cache-hit condition is recorded rather than left to be inferred`() {
        val saved = slot<EvaluationRun>()

        recorder.record(episode(), provenance(cacheHit = true))

        verify { evaluationRunRepository.save(capture(saved)) }
        assertTrue(saved.captured.cacheHit)
    }

    @Test
    fun `losing the record never fails the episode that produced it`() {
        // The episode is a deliverable in its own right; the experiment's bookkeeping is not.
        every { evaluationRunRepository.save(any<EvaluationRun>()) } throws RuntimeException("disk full")

        assertNull(recorder.record(episode(), provenance()))
    }

    @Test
    fun `an episode with no id is not recorded`() {
        assertNull(recorder.record(episode(id = null), provenance()))
        verify(exactly = 0) { evaluationRunRepository.save(any<EvaluationRun>()) }
    }
}
