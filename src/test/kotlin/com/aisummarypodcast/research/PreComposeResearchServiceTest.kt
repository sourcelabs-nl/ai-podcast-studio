package com.aisummarypodcast.research

import com.aisummarypodcast.llm.EpisodeHistoryRepository
import com.aisummarypodcast.llm.PastEpisodeMatch
import com.aisummarypodcast.store.Podcast
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PreComposeResearchServiceTest {

    private val deepDive = Podcast(id = "p1", userId = "u1", name = "Tech", topic = "AI", deepDiveEnabled = true)
    private val regular = deepDive.copy(deepDiveEnabled = false)

    private val planner = mockk<ResearchPlanner>()
    private val researchService = mockk<ResearchService>()
    private val historyRepository = mockk<EpisodeHistoryRepository> {
        every { search(any(), any(), any()) } returns emptyList()
    }
    private val recorder = mockk<ResearchSourceRecorder>(relaxed = true)
    private val service = PreComposeResearchService(planner, researchService, historyRepository, recorder)

    private fun hits(vararg titles: String) = TavilyResponse(
        results = titles.map { TavilyResult(title = it, url = "https://example.com/$it", content = "snippet $it") }
    )

    @Test
    fun `plans, searches and records the sources of a deep-dive episode`() = runTest {
        every { planner.plan(any()) } returns listOf("q1", "q2")
        every { researchService.search("u1", "q1", RESULTS_PER_QUERY) } returns hits("a", "b")
        every { researchService.search("u1", "q2", RESULTS_PER_QUERY) } returns hits("c")
        val recorded = slot<List<BackgroundSource>>()
        every { recorder.replace(230, capture(recorded)) } returns Unit

        val research = service.research(ResearchRequest(deepDive, listOf("o5", "EU AI Act"), episodeId = 230))

        assertEquals(2, research.researchCalls)
        assertEquals(listOf("a", "b", "c"), research.sources.map { it.title })
        assertEquals(listOf("q1", "q1", "q2"), research.sources.map { it.query })
        assertEquals("snippet a", research.sources.first().snippet)
        assertEquals(research.sources, recorded.captured)
    }

    @Test
    fun `a failing search contributes nothing and does not fail the run`() = runTest {
        every { planner.plan(any()) } returns listOf("q1", "q2", "q3")
        every { researchService.search("u1", "q1", any()) } returns hits("a")
        every { researchService.search("u1", "q2", any()) } throws RuntimeException("timeout")
        every { researchService.search("u1", "q3", any()) } returns hits("c")

        val research = service.research(ResearchRequest(deepDive, listOf("o5"), episodeId = 230))

        assertEquals(3, research.researchCalls)
        assertEquals(listOf("a", "c"), research.sources.map { it.title })
    }

    @Test
    fun `a regular podcast without deep dive runs no plan and no search but still gets history`() = runTest {
        val match = PastEpisodeMatch(5, "2026-09-20", "o5 preview", "The o5 preview.")
        every { historyRepository.search("p1", "o5", HISTORY_MATCHES_PER_SUBJECT) } returns listOf(match)

        val research = service.research(ResearchRequest(regular, listOf("o5"), episodeId = 230))

        assertEquals(listOf(match), research.history)
        assertTrue(research.sources.isEmpty())
        assertEquals(0, research.researchCalls)
        verify(exactly = 0) { planner.plan(any()) }
        verify(exactly = 0) { researchService.search(any(), any(), any()) }
        verify(exactly = 0) { recorder.replace(any(), any()) }
    }

    @Test
    fun `a focus episode researches even without deep dive`() = runTest {
        every { planner.plan(any()) } returns listOf("opus angle")
        every { researchService.search("u1", "opus angle", any()) } returns hits("a")

        val research = service.research(ResearchRequest(regular, listOf("Claude Opus 5.5"), focusEpisode = true, episodeId = 31))

        assertEquals(1, research.researchCalls)
        verify { recorder.replace(31, any()) }
    }

    @Test
    fun `history is looked up for at most five subjects and each episode is kept once`() = runTest {
        val subjects = (1..7).map { "s$it" }
        val shared = PastEpisodeMatch(5, "2026-09-20", "t", "r", isFocusEpisode = true)
        every { historyRepository.search("p1", any(), HISTORY_MATCHES_PER_SUBJECT) } returns listOf(shared)

        val research = service.research(ResearchRequest(regular, subjects))

        assertEquals(listOf(shared), research.history)
        assertTrue(research.history.single().isFocusEpisode)
        verify(exactly = HISTORY_SUBJECT_CAP) { historyRepository.search("p1", any(), any()) }
    }

    @Test
    fun `a preview records no sources`() = runTest {
        every { planner.plan(any()) } returns listOf("q1")
        every { researchService.search("u1", "q1", any()) } returns hits("a")

        val research = service.research(ResearchRequest(deepDive, listOf("o5"), episodeId = null))

        assertEquals(1, research.sources.size)
        verify(exactly = 0) { recorder.replace(any(), any()) }
    }
}
