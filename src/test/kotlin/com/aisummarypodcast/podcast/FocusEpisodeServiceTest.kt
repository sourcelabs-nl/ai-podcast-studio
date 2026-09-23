package com.aisummarypodcast.podcast

import com.aisummarypodcast.llm.ArticleEligibilityService
import com.aisummarypodcast.llm.ComposeStageResult
import com.aisummarypodcast.llm.EpisodeRecapGenerator
import com.aisummarypodcast.llm.LlmCostSource
import com.aisummarypodcast.llm.ModelResolver
import com.aisummarypodcast.llm.PipelineResult
import com.aisummarypodcast.llm.PipelineStage
import com.aisummarypodcast.llm.RecapResult
import com.aisummarypodcast.llm.ResolvedModel
import com.aisummarypodcast.llm.TokenUsage
import com.aisummarypodcast.store.ArticleRepository
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeArticle
import com.aisummarypodcast.store.EpisodeArticleRepository
import com.aisummarypodcast.store.EpisodeRepository
import com.aisummarypodcast.store.EpisodePurpose
import com.aisummarypodcast.store.EpisodeResearchSourceRepository
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastRepository
import com.aisummarypodcast.tts.TtsPipeline
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Optional

/** A focus episode stops for review, consumes no articles and leaves the regular schedule alone. */
class FocusEpisodeServiceTest {

    private val podcast = Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech", requireReview = false)
    private val filterModelDef = ResolvedModel(provider = "openrouter", model = "m", cost = null, stage = PipelineStage.FILTER)

    private val stored = mutableMapOf<Long, Episode>()
    private val episodeRepository = mockk<EpisodeRepository> {
        every { save(any()) } answers {
            val episode = firstArg<Episode>().let { if (it.id == null) it.copy(id = 5L) else it }
            stored[episode.id!!] = episode
            episode
        }
        every { findById(any<Long>()) } answers { Optional.ofNullable(stored[firstArg()]) }
    }
    private val podcastRepository = mockk<PodcastRepository>(relaxed = true) {
        every { findById(any<String>()) } returns Optional.of(podcast)
    }
    private val ttsPipeline = mockk<TtsPipeline>()
    private val episodeArticleRepository = mockk<EpisodeArticleRepository>(relaxed = true) {
        every { findByEpisodeId(any()) } returns listOf(EpisodeArticle(episodeId = 5L, articleId = 10L))
        every { findArticlesWithTopicsByEpisodeId(any()) } returns emptyList()
    }
    private val articleRepository = mockk<ArticleRepository>(relaxed = true)
    private val episodeRecapGenerator = mockk<EpisodeRecapGenerator> {
        coEvery { generate(any(), any(), any(), any(), any()) } returns RecapResult(
            recap = "Recap.", usage = TokenUsage(10, 5), costCents = 0, costSource = LlmCostSource.TABLE
        )
    }
    private val modelResolver = mockk<ModelResolver> {
        every { resolve(any(), PipelineStage.FILTER) } returns filterModelDef
    }
    private val researchSourceRepository = mockk<EpisodeResearchSourceRepository>(relaxed = true)

    // Judge mode is OFF: scoring is not under test here, and background judging must not fire.
    private val appProperties = mockk<com.aisummarypodcast.config.AppProperties>(relaxed = true) {
        every { eval } returns com.aisummarypodcast.config.EvalProperties(
            com.aisummarypodcast.config.JudgeProperties(com.aisummarypodcast.config.JudgeMode.OFF, null)
        )
    }

    private val service = EpisodeService(
        episodeRepository, podcastRepository, ttsPipeline,
        episodeArticleRepository, mockk(relaxed = true), articleRepository,
        episodeRecapGenerator, modelResolver, mockk(relaxed = true),
        mockk(relaxed = true), mockk<ArticleEligibilityService>(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true),
        appProperties,
        researchSourceRepository,
        mockk(relaxed = true)
    )

    private fun focusEpisode(status: EpisodeStatus = EpisodeStatus.GENERATING) = Episode(
        id = 5L, podcastId = "p1", generatedAt = "2026-09-22T08:00:00Z", scriptText = "script",
        status = status, focus = "Claude Opus 5.5 release"
    ).also { stored[5L] = it }

    @Test
    fun `finalizing a focus episode stops at review even when the podcast does not require it`() = runTest {
        val episode = service.finalizeEpisode(focusEpisode(), podcast)

        assertEquals(EpisodeStatus.PENDING_REVIEW, episode.status)
        coVerify(exactly = 0) { ttsPipeline.generateForExistingEpisode(any(), any()) }
    }

    @Test
    fun `finalizing a focus episode marks no article processed and keeps lastGeneratedAt`() = runTest {
        service.finalizeEpisode(focusEpisode(), podcast)

        verify(exactly = 0) { articleRepository.save(any()) }
        verify(exactly = 0) { podcastRepository.save(any()) }
    }

    @Test
    fun `creating a focus episode from a pipeline result keeps its articles unconsumed`() = runTest {
        val result = PipelineResult(script = "s", filterModel = "f", composeModel = "c", processedArticleIds = listOf(10L))

        val episode = service.createEpisodeFromPipelineResult(podcast, result, generatingEpisode = focusEpisode())

        assertEquals(EpisodeStatus.PENDING_REVIEW, episode.status)
        verify { episodeArticleRepository.insertIgnore(5L, 10L, any(), any(), any()) }
        verify(exactly = 0) { articleRepository.save(any()) }
        verify(exactly = 0) { podcastRepository.save(any()) }
    }

    @Test
    fun `creating a focus GENERATING episode does not advance the schedule`() {
        val window = EpisodeWindow(
            start = java.time.Instant.parse("2026-09-21T06:00:00Z"), end = java.time.Instant.parse("2026-09-22T06:00:00Z")
        )

        val episode = service.createGeneratingEpisode(podcast, window, focus = "Claude Opus 5.5 release")

        assertEquals("Claude Opus 5.5 release", episode.focus)
        verify(exactly = 0) { podcastRepository.save(any()) }
    }

    @Test
    fun `a failed focus episode does not satisfy the schedule`() {
        service.failEpisode(podcast, "No relevant articles found for focus", focusEpisode())

        assertEquals(EpisodeStatus.FAILED, stored.getValue(5L).status)
        verify(exactly = 0) { podcastRepository.save(any()) }
    }

    @Test
    fun `feedback recompose updates the same episode and keeps the latest feedback`() {
        focusEpisode(EpisodeStatus.PENDING_REVIEW)
        val compose = { script: String ->
            ComposeStageResult(
                script = script, composeModel = "c", usage = TokenUsage(1, 1), topicOrder = emptyList(),
                composeCostCents = 1, composeCostSource = LlmCostSource.TABLE
            )
        }

        service.saveFeedbackRecompose(stored.getValue(5L), compose("first"), "make it shorter")
        val second = service.saveFeedbackRecompose(stored.getValue(5L), compose("second"), "focus on benchmarks")

        assertEquals(5L, second.id)
        assertEquals("second", second.scriptText)
        assertEquals("focus on benchmarks", second.reviewFeedback)
        assertEquals(EpisodeStatus.PENDING_REVIEW, second.status)
    }

    @Test
    fun `a focus episode blocks another focus episode but not a regular one`() {
        every { episodeRepository.findByPodcastIdAndStatusInAndPurposeNot("p1", any<Collection<EpisodeStatus>>(), EpisodePurpose.EXPERIMENT) } returns
            listOf(focusEpisode(EpisodeStatus.PENDING_REVIEW))

        assertFalse(service.hasActiveEpisode("p1"))
        assertTrue(service.hasActiveEpisode("p1", focusEpisodes = true))
    }

    @Test
    fun `recent focus episodes are only those after the last regular episode`() {
        every { episodeRepository.findByPodcastIdAndStatus("p1", EpisodeStatus.GENERATED) } returns listOf(
            Episode(id = 1, podcastId = "p1", generatedAt = "2026-09-19T06:00:00Z", scriptText = "", focus = "old focus"),
            Episode(id = 2, podcastId = "p1", generatedAt = "2026-09-20T06:00:00Z", scriptText = ""),
            Episode(id = 3, podcastId = "p1", generatedAt = "2026-09-21T12:00:00Z", scriptText = "", focus = "Claude Opus 5.5 release")
        )

        val recent = service.findRecentFocusEpisodes("p1")

        assertEquals(listOf("Claude Opus 5.5 release"), recent.map { it.focus })
    }
}
