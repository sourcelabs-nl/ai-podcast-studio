package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.EvalProperties
import com.aisummarypodcast.config.JudgeMode
import com.aisummarypodcast.config.JudgeProperties
import com.aisummarypodcast.eval.EpisodeScoringService
import com.aisummarypodcast.llm.ArticleEligibilityService
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
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Optional

/** Every route that finishes a script runs the same epilogue: judge in the background, then recap, show notes and sources. */
class EpisodeEpilogueTest {

    private val podcast = Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech", requireReview = true)
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
        every { save(any()) } answers { firstArg() }
    }
    private val articleRepository = mockk<ArticleRepository> {
        every { findById(any<Long>()) } returns Optional.empty()
    }
    private val episodeArticleRepository = mockk<EpisodeArticleRepository>(relaxed = true) {
        every { findByEpisodeId(any()) } returns listOf(EpisodeArticle(episodeId = 5L, articleId = 10L, topic = "AI", topicOrder = 0))
        every { findArticlesWithTopicsByEpisodeId(any()) } returns emptyList()
    }
    private val episodeRecapGenerator = mockk<EpisodeRecapGenerator> {
        coEvery { generate(any(), any(), any(), any(), any()) } returns RecapResult(
            recap = "Recap.", usage = TokenUsage(10, 5), costCents = 0, costSource = LlmCostSource.TABLE
        )
    }
    private val modelResolver = mockk<ModelResolver> {
        every { resolve(any(), PipelineStage.FILTER) } returns filterModelDef
    }
    private val episodeSourcesGenerator = mockk<EpisodeSourcesGenerator>(relaxed = true)
    private val scoringService = mockk<EpisodeScoringService> {
        coEvery { scoreEpisode(any()) } returns null
    }

    private fun serviceWith(mode: JudgeMode): EpisodeService {
        val appProperties = mockk<AppProperties>(relaxed = true) {
            every { eval } returns EvalProperties(JudgeProperties(mode, null))
        }
        return EpisodeService(
            episodeRepository, podcastRepository, mockk(),
            episodeArticleRepository, mockk(relaxed = true), articleRepository,
            episodeRecapGenerator, modelResolver, mockk(relaxed = true),
            episodeSourcesGenerator, mockk<ArticleEligibilityService>(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true),
            appProperties,
            mockk(relaxed = true),
            scoringService
        )
    }

    private fun episode(recap: String? = null) = Episode(
        id = 5L, podcastId = "p1", generatedAt = "2026-09-22T08:00:00Z", scriptText = "script",
        status = EpisodeStatus.GENERATING, recap = recap
    ).also { stored[5L] = it }

    @Test
    fun `finalizing an episode judges it once and generates recap, show notes and sources`() = runTest {
        val finalized = serviceWith(JudgeMode.ADVISE).finalizeEpisode(episode(), podcast, listOf("AI"))

        assertEquals("Recap.", finalized.recap)
        coVerify(timeout = 2000, exactly = 1) { scoringService.scoreEpisode(match { it.id == 5L }) }
        verify { episodeSourcesGenerator.generate(any(), podcast, any()) }
    }

    @Test
    fun `a retry resuming with an existing recap keeps it and is still judged`() = runTest {
        val finalized = serviceWith(JudgeMode.ADVISE).finalizeEpisode(episode(recap = "Old recap."), podcast)

        assertEquals("Old recap.", finalized.recap)
        coVerify(exactly = 0) { episodeRecapGenerator.generate(any(), any(), any(), any(), any()) }
        coVerify(timeout = 2000, exactly = 1) { scoringService.scoreEpisode(any()) }
    }

    @Test
    fun `a regeneration from a pipeline result is judged and gets recap, show notes and sources`() = runTest {
        val result = PipelineResult(script = "s", filterModel = "f", composeModel = "c", topicOrder = listOf("AI"))

        val created = serviceWith(JudgeMode.ADVISE).createEpisodeFromPipelineResult(podcast, result, generatingEpisode = episode())

        assertEquals("Recap.", created.recap)
        coVerify(timeout = 2000, exactly = 1) { scoringService.scoreEpisode(match { it.scriptText == "s" }) }
        verify { episodeSourcesGenerator.generate(any(), podcast, any()) }
    }

    @Test
    fun `a rewritten script is judged and its recap regenerated from its linked topics`() = runTest {
        val rewritten = serviceWith(JudgeMode.ADVISE).runEpilogueForRewrite(episode(recap = "Old recap."), podcast)

        assertEquals("Recap.", rewritten.recap)
        coVerify { episodeRecapGenerator.generate(any(), podcast, filterModelDef, listOf("AI"), 5L) }
        coVerify(timeout = 2000, exactly = 1) { scoringService.scoreEpisode(any()) }
    }

    @Test
    fun `judge mode OFF makes no judge call`() = runTest {
        serviceWith(JudgeMode.OFF).finalizeEpisode(episode(), podcast)

        Thread.sleep(200)
        coVerify(exactly = 0) { scoringService.scoreEpisode(any()) }
    }

    @Test
    fun `a failing judge leaves the route's result intact`() = runTest {
        coEvery { scoringService.scoreEpisode(any()) } throws RuntimeException("judge down")

        val finalized = serviceWith(JudgeMode.ADVISE).finalizeEpisode(episode(), podcast)

        assertEquals(EpisodeStatus.PENDING_REVIEW, finalized.status)
        coVerify(timeout = 2000) { scoringService.scoreEpisode(any()) }
    }
}
