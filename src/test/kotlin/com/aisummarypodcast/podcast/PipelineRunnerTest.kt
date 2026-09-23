package com.aisummarypodcast.podcast

import com.aisummarypodcast.llm.ComposeContext
import com.aisummarypodcast.llm.ComposeStageResult
import com.aisummarypodcast.llm.DedupStageResult
import com.aisummarypodcast.llm.FilteredArticle
import com.aisummarypodcast.llm.FocusSelection
import com.aisummarypodcast.llm.LlmCostSource
import com.aisummarypodcast.llm.LlmPipeline
import com.aisummarypodcast.llm.PipelineResult
import com.aisummarypodcast.llm.PreviewResult
import com.aisummarypodcast.llm.RecentFocusEpisode
import com.aisummarypodcast.llm.TokenUsage
import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.Podcast
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.time.LocalDate

/**
 * Pins what each route's run does: which stages run, what is persisted, which stage events are
 * emitted and how a failure is handled. The episode service is a strict mock, so a persistence call
 * a run is not expected to make fails the test.
 */
class PipelineRunnerTest {

    private val podcast = Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech")
    private val window = EpisodeWindow(
        start = Instant.parse("2026-08-30T13:00:00Z"),
        end = Instant.parse("2026-08-31T13:00:00Z")
    )
    private val episodeDate = LocalDate.of(2026, 8, 31)
    private val episode = Episode(
        id = 7L, podcastId = "p1", generatedAt = "2026-08-31T13:00:00Z", scriptText = "",
        status = EpisodeStatus.GENERATING
    )
    private val article = Article(
        id = 1L, sourceId = "s1", title = "A", body = "b", url = "https://example.com/a", contentHash = "h"
    )
    private val dedupResult = DedupStageResult(
        filteredArticles = listOf(FilteredArticle(article, followUpContext = "follow", topic = "Topic")),
        filterModel = "f", dedupModel = "d", usage = TokenUsage(1, 1),
        followUpAnnotations = mapOf(1L to "follow"), topicLabels = listOf("Topic"),
        dedupCostCents = 1, dedupCostSource = LlmCostSource.API
    )
    private val composeResult = ComposeStageResult(
        script = "script", composeModel = "c", usage = TokenUsage(1, 1), topicOrder = listOf("Topic"),
        composeCostCents = 1, composeCostSource = LlmCostSource.API
    )
    private val focusSelection = FocusSelection(
        articles = listOf(FilteredArticle(article)), filterModel = "f", scoreInputTokens = 0,
        scoreOutputTokens = 0, scoreCostCents = 0, scoreCostSource = LlmCostSource.API, scoreReportedCostCents = null
    )
    private val recentFocus = listOf(RecentFocusEpisode("Opus", "2026-08-30T10:00:00Z"))

    private val llmPipeline = mockk<LlmPipeline>()
    private val episodeService = mockk<EpisodeService> {
        every { updatePipelineStage(any(), any()) } returns Unit
    }
    private val episodeWindowResolver = mockk<EpisodeWindowResolver> {
        every { episodeDateOf(any(), any()) } returns episodeDate
    }
    private val events = mutableListOf<PodcastEvent>()
    private val eventPublisher = mockk<ApplicationEventPublisher> {
        every { publishEvent(any<ApplicationEvent>()) } answers { (firstArg<Any>() as? PodcastEvent)?.let(events::add); Unit }
    }

    private val runner = PipelineRunner(llmPipeline, episodeService, episodeWindowResolver, eventPublisher)

    private fun spec(
        purpose: RunPurpose,
        input: RunInput = RunInput.Window(window),
        resumePoint: ResumePoint = ResumePoint.FULL_PIPELINE,
        outcome: RunOutcome = RunOutcome.Deliver(),
        target: Episode? = episode
    ) = RunSpec(podcast, target, purpose, input, resumePoint, outcome)

    private fun stageNames() = events.filter { it.event == "episode.stage" }.map { it.data["stage"] }

    private fun stubSelection() {
        coEvery { llmPipeline.aggregateScoreAndFilter(podcast, window, 7L, any()) } returns listOf(article)
        coEvery { llmPipeline.dedup(listOf(article), podcast, 7L, any()) } answers {
            arg<(String, Map<String, Any>) -> Unit>(3)("deduplicating", emptyMap())
            dedupResult
        }
        every { episodeService.saveDedupResults(episode, dedupResult) } returns Unit
        coEvery { llmPipeline.compose(any(), podcast, any(), any()) } returns composeResult
        every { episodeService.saveComposeResult(episode, composeResult) } returns Unit
        coEvery { episodeService.finalizeEpisode(episode, podcast, listOf("Topic")) } returns episode
    }

    // --- Types ------------------------------------------------------------------------------------

    @Test
    fun `every input and outcome variant builds a run spec`() {
        val linked = LinkedArticlesResult(listOf(article), emptyList(), emptyMap())
        val inputs = listOf(RunInput.Window(window), RunInput.ArticleSet(linked, window), RunInput.Focus("Opus", window))
        val outcomes = listOf(RunOutcome.Deliver(), RunOutcome.Review("shorter"), RunOutcome.Transient())

        for (input in inputs) for (outcome in outcomes) {
            assertEquals(input, spec(RunPurpose.MANUAL, input = input, outcome = outcome).input)
        }
    }

    @Test
    fun `only a transient run may have no episode`() {
        assertThrows(IllegalArgumentException::class.java) { spec(RunPurpose.MANUAL, target = null) }
    }

    // --- Preview (Transient) ----------------------------------------------------------------------

    @Test
    fun `a transient run previews the window and touches no episode`() = runTest {
        val preview = PreviewResult(script = "preview", articleIds = listOf(1L))
        val progress = mutableListOf<String>()
        coEvery { llmPipeline.preview(podcast, window, any()) } answers {
            arg<(String, Map<String, Any>) -> Unit>(2)("composing", emptyMap())
            preview
        }

        val result = runner.run(
            spec(RunPurpose.PREVIEW, outcome = RunOutcome.Transient { stage, _ -> progress += stage }, target = null)
        )

        assertEquals(RunResult.Previewed(preview), result)
        assertEquals(listOf("composing"), progress)
        // Strict mock: the only stubbed call is the stage persistence of an episode run, and a
        // preview must not make it either.
        verify(exactly = 0) { episodeService.updatePipelineStage(any(), any()) }
        assertEquals(emptyList<PodcastEvent>(), events)
    }

    @Test
    fun `a failing transient run propagates its failure to the caller`() = runTest {
        coEvery { llmPipeline.preview(podcast, window, any()) } throws IllegalStateException("dedup down")

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { runner.run(spec(RunPurpose.PREVIEW, outcome = RunOutcome.Transient(), target = null)) }
        }
    }

    // --- Scheduled, manual and re-run generation ----------------------------------------------------

    @Test
    fun `a generation selects, dedups, composes with recent focus episodes and finalizes`() = runTest {
        stubSelection()
        every { episodeService.findRecentFocusEpisodes("p1") } returns recentFocus

        val result = runner.run(spec(RunPurpose.SCHEDULED))

        assertEquals(RunResult.Completed(episode), result)
        coVerify {
            llmPipeline.compose(
                dedupResult.filteredArticles, podcast,
                ComposeContext(
                    followUpAnnotations = mapOf(1L to "follow"), topicLabels = listOf("Topic"),
                    episodeDate = episodeDate, episodeId = 7L, recentFocusEpisodes = recentFocus
                ),
                any()
            )
        }
        assertEquals(
            listOf("deduplicating", "dedup_saved", "script_saved", "marking_processed", "generating_recap"),
            stageNames()
        )
        verify(exactly = 1) { episodeService.updatePipelineStage(7L, "deduplicating") }
        coVerify { episodeService.finalizeEpisode(episode, podcast, listOf("Topic"), true) }
    }

    @Test
    fun `a re-run does not bump lastGeneratedAt, so it does not move the schedule`() = runTest {
        stubSelection()
        every { episodeService.findRecentFocusEpisodes("p1") } returns emptyList()
        coEvery { episodeService.finalizeEpisode(episode, podcast, listOf("Topic"), false) } returns episode

        runner.run(spec(RunPurpose.RERUN, outcome = RunOutcome.Deliver(updateLastGenerated = false)))

        coVerify { episodeService.finalizeEpisode(episode, podcast, listOf("Topic"), false) }
    }

    @Test
    fun `a scheduled generation bumps lastGeneratedAt`() = runTest {
        stubSelection()
        every { episodeService.findRecentFocusEpisodes("p1") } returns emptyList()

        runner.run(spec(RunPurpose.SCHEDULED))

        coVerify { episodeService.finalizeEpisode(episode, podcast, listOf("Topic"), true) }
    }

    @Test
    fun `a generation with nothing eligible deletes its placeholder episode`() = runTest {
        coEvery { llmPipeline.aggregateScoreAndFilter(podcast, window, 7L, any()) } returns null
        every { episodeService.deleteGeneratingEpisode(7L) } returns Unit

        val result = runner.run(spec(RunPurpose.MANUAL))

        assertEquals(RunResult.NothingToCompose, result)
        verify { episodeService.deleteGeneratingEpisode(7L) }
    }

    @Test
    fun `a failed generation fails its episode and reports the error`() = runTest {
        val failed = episode.copy(status = EpisodeStatus.FAILED)
        coEvery { llmPipeline.aggregateScoreAndFilter(podcast, window, 7L, any()) } throws IllegalStateException("boom")
        every { episodeService.failEpisode(podcast, "boom", episode) } returns failed

        val result = runner.run(spec(RunPurpose.SCHEDULED))

        assertEquals(RunResult.Failed(failed, "boom"), result)
    }

    // --- Retry --------------------------------------------------------------------------------------

    @Test
    fun `a full retry composes without recent focus episodes and emits only pipeline stages`() = runTest {
        stubSelection()

        runner.run(spec(RunPurpose.RETRY))

        coVerify {
            llmPipeline.compose(
                dedupResult.filteredArticles, podcast,
                ComposeContext(
                    followUpAnnotations = mapOf(1L to "follow"), topicLabels = listOf("Topic"),
                    episodeDate = episodeDate, episodeId = 7L
                ),
                any()
            )
        }
        verify(exactly = 0) { episodeService.findRecentFocusEpisodes(any()) }
        assertEquals(listOf("deduplicating"), stageNames())
    }

    @Test
    fun `a full retry with nothing eligible fails the episode rather than deleting it`() = runTest {
        coEvery { llmPipeline.aggregateScoreAndFilter(podcast, window, 7L, any()) } returns null
        every { episodeService.failEpisode(podcast, any(), episode) } returns episode

        val result = runner.run(spec(RunPurpose.RETRY))

        assertInstanceOf(RunResult.Failed::class.java, result)
        verify { episodeService.failEpisode(podcast, match { it.startsWith("No eligible articles for retry") }, episode) }
        verify(exactly = 0) { episodeService.deleteGeneratingEpisode(any()) }
    }

    @Test
    fun `a retry from compose recomposes the linked articles with their topics`() = runTest {
        every { episodeService.findLinkedArticlesAndTopics(7L) } returns
            LinkedArticlesResult(listOf(article), listOf("Topic"), mapOf(1L to "Topic"), mapOf(1L to "follow"))
        coEvery { llmPipeline.compose(any(), podcast, any(), any()) } returns composeResult
        every { episodeService.saveComposeResult(episode, composeResult) } returns Unit
        coEvery { episodeService.finalizeEpisode(episode, podcast, listOf("Topic")) } returns episode

        runner.run(spec(RunPurpose.RETRY, resumePoint = ResumePoint.COMPOSE))

        coVerify {
            llmPipeline.compose(
                listOf(FilteredArticle(article, topic = "Topic")), podcast,
                ComposeContext(topicLabels = listOf("Topic"), episodeDate = episodeDate, episodeId = 7L),
                any()
            )
        }
    }

    @Test
    fun `a retry after compose only finalizes with the linked topics`() = runTest {
        every { episodeService.findLinkedArticlesAndTopics(7L) } returns
            LinkedArticlesResult(listOf(article), listOf("Topic"), mapOf(1L to "Topic"))
        coEvery { episodeService.finalizeEpisode(episode, podcast, listOf("Topic")) } returns episode

        val result = runner.run(
            spec(RunPurpose.RETRY, input = RunInput.Focus("Opus", window), resumePoint = ResumePoint.POST_COMPOSE)
        )

        assertEquals(RunResult.Completed(episode), result)
        coVerify(exactly = 0) { llmPipeline.compose(any(), any(), any(), any()) }
    }

    // --- Focus and feedback recompose --------------------------------------------------------------

    @Test
    fun `a focus run selects against the focus, saves the selection and finalizes`() = runTest {
        coEvery { llmPipeline.selectForFocus(podcast, window, "Opus", 7L, any()) } returns focusSelection
        every { episodeService.saveFocusSelection(episode, focusSelection) } returns Unit
        coEvery { llmPipeline.compose(focusSelection.articles, podcast, any(), any()) } returns composeResult
        every { episodeService.saveComposeResult(episode, composeResult) } returns Unit
        coEvery { episodeService.finalizeEpisode(episode, podcast, listOf("Topic")) } returns episode

        runner.run(spec(RunPurpose.FOCUS, input = RunInput.Focus("Opus", window)))

        coVerify {
            llmPipeline.compose(
                focusSelection.articles, podcast,
                ComposeContext(episodeDate = episodeDate, episodeId = 7L, focus = "Opus"),
                any()
            )
        }
    }

    @Test
    fun `a focus retry from compose rescores the linked set against the focus`() = runTest {
        every { episodeService.findLinkedArticlesAndTopics(7L) } returns
            LinkedArticlesResult(listOf(article), emptyList(), emptyMap())
        coEvery { llmPipeline.scoreForFocus(podcast, listOf(article), "Opus", 7L, any()) } returns focusSelection
        coEvery { llmPipeline.compose(focusSelection.articles, podcast, any(), any()) } returns composeResult
        every { episodeService.saveComposeResult(episode, composeResult) } returns Unit
        coEvery { episodeService.finalizeEpisode(episode, podcast, listOf("Topic")) } returns episode

        runner.run(spec(RunPurpose.RETRY, input = RunInput.Focus("Opus", window), resumePoint = ResumePoint.COMPOSE))

        coVerify(exactly = 0) { llmPipeline.selectForFocus(any(), any(), any(), any(), any()) }
        coVerify { episodeService.finalizeEpisode(episode, podcast, listOf("Topic")) }
    }

    @Test
    fun `a failed feedback recompose leaves the episode in review`() = runTest {
        every { episodeService.findLinkedArticlesAndTopics(7L) } returns
            LinkedArticlesResult(listOf(article), emptyList(), emptyMap())
        coEvery { llmPipeline.scoreForFocus(any(), any(), any(), any(), any()) } throws IllegalStateException("down")
        every { episodeService.clearPipelineStage(7L) } returns Unit

        val result = runner.run(
            spec(
                RunPurpose.RECOMPOSE, input = RunInput.Focus("Opus", window),
                resumePoint = ResumePoint.COMPOSE, outcome = RunOutcome.Review("shorter")
            )
        )

        assertEquals(RunResult.Failed(null, "down"), result)
        verify { episodeService.clearPipelineStage(7L) }
        verify(exactly = 0) { episodeService.failEpisode(any(), any(), any()) }
        assertEquals(listOf("episode.recompose_failed"), events.map { it.event })
    }

    // --- Regeneration -------------------------------------------------------------------------------

    @Test
    fun `a regeneration recomposes the article set into a new episode without persisting stages`() = runTest {
        val linked = LinkedArticlesResult(listOf(article), listOf("Topic"), mapOf(1L to "Topic"), mapOf(1L to "follow"))
        val recomposed = PipelineResult(script = "s", filterModel = "f", composeModel = "c")
        coEvery { llmPipeline.recompose(listOf(article), podcast, any(), any()) } answers {
            arg<(String, Map<String, Any>) -> Unit>(3)("composing", emptyMap())
            recomposed
        }
        coEvery { episodeService.createEpisodeFromPipelineResult(any(), any(), any(), any(), any()) } returns episode

        runner.run(
            RunSpec(
                podcast, episode, RunPurpose.REGENERATE, RunInput.ArticleSet(linked, window), ResumePoint.COMPOSE,
                RunOutcome.Deliver(updateLastGenerated = false, generatedAt = "2026-08-31T13:00:00Z"),
                bypassLlmCache = true
            )
        )

        coVerify {
            llmPipeline.recompose(
                listOf(article), podcast,
                ComposeContext(
                    followUpAnnotations = mapOf(1L to "follow"), topicLabels = listOf("Topic"),
                    episodeDate = episodeDate, bypassLlmCache = true, episodeId = 7L
                ),
                any()
            )
        }
        coVerify {
            episodeService.createEpisodeFromPipelineResult(
                podcast, recomposed.copy(articleTopics = mapOf(1L to "Topic")), episode,
                "2026-08-31T13:00:00Z", false
            )
        }
        assertEquals(listOf("composing"), stageNames())
        verify(exactly = 0) { episodeService.updatePipelineStage(any(), any()) }
    }
}
