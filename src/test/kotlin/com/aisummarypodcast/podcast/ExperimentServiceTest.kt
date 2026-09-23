package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.BriefingProperties
import com.aisummarypodcast.config.EncryptionProperties
import com.aisummarypodcast.config.EpisodesProperties
import com.aisummarypodcast.config.ExperimentsProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.LlmProperties
import com.aisummarypodcast.eval.EpisodeScoringService
import com.aisummarypodcast.llm.PipelineStage
import com.aisummarypodcast.llm.RunOverrides
import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodePurpose
import com.aisummarypodcast.store.EpisodeScore
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.LlmCallRepository
import com.aisummarypodcast.store.LlmCallRow
import com.aisummarypodcast.store.Podcast
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class ExperimentServiceTest {

    private val episodeService = mockk<EpisodeService>()
    private val pipelineRunner = mockk<PipelineRunner>()
    private val episodeWindowResolver = mockk<EpisodeWindowResolver>()
    private val episodeScoringService = mockk<EpisodeScoringService>()
    private val llmCallRepository = mockk<LlmCallRepository>()
    private val appProperties = AppProperties(
        llm = LlmProperties(),
        briefing = BriefingProperties(),
        episodes = EpisodesProperties(),
        feed = FeedProperties(),
        encryption = EncryptionProperties(masterKey = "test-key"),
        experiments = ExperimentsProperties(maxVariantRepeats = 6)
    )
    private val service = ExperimentService(
        episodeService, pipelineRunner, episodeWindowResolver, episodeScoringService, llmCallRepository, appProperties
    )

    private val podcast = Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech")
    private val window = EpisodeWindow(Instant.parse("2026-09-20T00:00:00Z"), Instant.parse("2026-09-21T00:00:00Z"))
    private val source = Episode(
        id = 10, podcastId = "p1", generatedAt = "2026-09-21T06:00:00Z", scriptText = "script",
        windowStart = window.startIso, windowEnd = window.endIso
    )
    private val article = Article(
        id = 1, sourceId = "s1", title = "A", body = "body", url = "https://example.com/1", contentHash = "h1"
    )
    private val linked = LinkedArticlesResult(listOf(article), listOf("topic"), mapOf(1L to "topic"))

    private val throughput = ExperimentVariant("throughput", RunOverrides(providerSort = "throughput"))
    private val lowEffort = ExperimentVariant("low", RunOverrides(reasoningEffort = mapOf(PipelineStage.COMPOSE to "low")))

    private fun stubStart() {
        every { episodeService.findLinkedArticlesAndTopics(10) } returns linked
        every { episodeWindowResolver.windowOf(source) } returns window
        var nextId = 100L
        every { episodeService.createExperimentEpisode(podcast, window, any()) } answers {
            val identity = thirdArg<ExperimentRunIdentity>()
            Episode(
                id = nextId++, podcastId = "p1", generatedAt = "2026-09-23T10:00:00Z", scriptText = "",
                status = EpisodeStatus.GENERATING, purpose = EpisodePurpose.EXPERIMENT,
                experimentId = identity.experimentId, experimentVariant = identity.variant,
                experimentSourceEpisodeId = identity.sourceEpisodeId
            )
        }
    }

    @Test
    fun `an oversized experiment is refused before any episode or run`() {
        val plan = ExperimentPlan(listOf(throughput, lowEffort), repeats = 4)

        assertThrows<ExperimentTooLargeException> { service.startExperiment(source, podcast, plan) }

        verify(exactly = 0) { episodeService.createExperimentEpisode(any(), any(), any()) }
        coVerify(exactly = 0) { pipelineRunner.run(any()) }
    }

    @Test
    fun `N variants x k repeats make N times k sandboxed experiment runs with their overrides`() {
        stubStart()
        val specs = mutableListOf<RunSpec>()
        coEvery { pipelineRunner.run(capture(specs)) } answers { RunResult.Completed(firstArg<RunSpec>().episode!!) }

        val started = service.startExperiment(source, podcast, ExperimentPlan(listOf(throughput, lowEffort), repeats = 3))

        assertEquals((100L..105L).toList(), started.episodeIds)
        coVerify(exactly = 6, timeout = 5000) { pipelineRunner.run(any()) }
        assertEquals(6, specs.size)
        specs.forEach { spec ->
            assertEquals(RunPurpose.EXPERIMENT, spec.purpose)
            assertEquals(RunOutcome.Sandbox, spec.outcome)
            assertEquals(RunInput.ArticleSet(linked, window), spec.input)
            assertEquals(ResumePoint.COMPOSE, spec.resumePoint)
            assertEquals(started.experimentId, spec.episode!!.experimentId)
        }
        // Interleaved by repeat, and every variant bypasses the cache so its repeats differ.
        assertEquals(listOf("throughput", "low", "throughput", "low", "throughput", "low"), specs.map { it.episode!!.experimentVariant })
        assertEquals(
            listOf(RunOverrides(providerSort = "throughput", bypassLlmCache = true)),
            specs.filter { it.episode!!.experimentVariant == "throughput" }.map { it.overrides }.distinct()
        )
        assertEquals(
            listOf(RunOverrides(reasoningEffort = mapOf(PipelineStage.COMPOSE to "low"), bypassLlmCache = true)),
            specs.filter { it.episode!!.experimentVariant == "low" }.map { it.overrides }.distinct()
        )
    }

    @Test
    fun `a variant that asks for the cache keeps it`() {
        stubStart()
        val spec = slot<RunSpec>()
        coEvery { pipelineRunner.run(capture(spec)) } answers { RunResult.Completed(firstArg<RunSpec>().episode!!) }

        service.startExperiment(source, podcast, ExperimentPlan(listOf(ExperimentVariant("cached", RunOverrides(bypassLlmCache = false))), 1))

        coVerify(exactly = 1, timeout = 5000) { pipelineRunner.run(any()) }
        assertEquals(false, spec.captured.overrides!!.bypassLlmCache)
    }

    @Test
    fun `an episode without linked articles or an experiment episode cannot be experimented on`() {
        every { episodeService.findLinkedArticlesAndTopics(10) } returns LinkedArticlesResult(emptyList(), emptyList(), emptyMap())
        val plan = ExperimentPlan(listOf(throughput), 1)

        assertThrows<EpisodeNotExperimentableException> { service.startExperiment(source, podcast, plan) }
        assertThrows<EpisodeNotExperimentableException> {
            service.startExperiment(source.copy(purpose = EpisodePurpose.EXPERIMENT), podcast, plan)
        }
        coVerify(exactly = 0) { pipelineRunner.run(any()) }
    }

    private fun run(id: Long, variant: String, status: EpisodeStatus, script: String = "", composeCents: Double = 0.0) = Episode(
        id = id, podcastId = "p1", generatedAt = "2026-09-23T10:00:0${id % 10}Z", scriptText = script, status = status,
        purpose = EpisodePurpose.EXPERIMENT, experimentId = "exp-1", experimentVariant = variant,
        experimentSourceEpisodeId = 10, composeReportedCostCents = composeCents.takeIf { status == EpisodeStatus.GENERATED },
        recapCostCents = 1, researchCostCents = 2
    )

    private fun composeCall(startedAt: String, durationMs: Long, provider: String, reasoning: Int?) = LlmCallRow(
        startedAt = startedAt, stage = "compose", model = "m", durationMs = durationMs, outcome = "ok",
        cacheHit = false, servedProvider = provider, reasoningTokens = reasoning
    )

    private fun score(episodeId: Long, overall: Double) = EpisodeScore(
        episodeId = episodeId, scorerVersion = 1, judgeModel = "judge", scoredAt = "2026-09-23T11:00:00Z",
        overall = overall, cliffhangerScore = 0.0, humorScore = 0.0, teaserScore = 0.0, promises = 0,
        deferredPromises = 0, unpaidPromises = 0, medianDeferralTurns = null, humorBeats = 0,
        humorSpeakerBalance = 0.0, humorReactionRatio = 0.0, teaserTopics = 0, anchorsJson = "{}",
        inputTokens = 0, outputTokens = 0, costCents = 0
    )

    @Test
    fun `the comparison reports every run and the means of each variant's completed runs`() {
        every { episodeService.findExperimentEpisodes(10) } returns listOf(
            run(101, "throughput", EpisodeStatus.GENERATED, "one two three four", composeCents = 4.0),
            run(102, "throughput", EpisodeStatus.GENERATED, "one two", composeCents = 6.0),
            run(103, "baseline", EpisodeStatus.FAILED)
        )
        every { llmCallRepository.requestsForEpisode(101) } returns listOf(
            composeCall("2026-09-23T10:00:00Z", 1_000, "Novita", 30),
            composeCall("2026-09-23T10:00:05Z", 2_000, "Parasail", 10),
            LlmCallRow("2026-09-23T10:01:00Z", "filter", "m", 500, "ok", false, "Novita", 0)
        )
        every { llmCallRepository.requestsForEpisode(102) } returns listOf(composeCall("2026-09-23T10:02:00Z", 3_000, "Novita", null))
        every { llmCallRepository.requestsForEpisode(103) } returns emptyList()
        every { episodeScoringService.existingScores(101) } returns listOf(score(101, 0.8))
        every { episodeScoringService.existingScores(102) } returns listOf(score(102, 0.6))
        every { episodeScoringService.existingScores(103) } returns emptyList()

        val comparison = service.compare(source)

        val experiment = comparison.experiments.single()
        assertEquals("exp-1", experiment.experimentId)
        val throughputResult = experiment.variants.first { it.name == "throughput" }
        val first = throughputResult.runs.first { it.episodeId == 101L }
        assertEquals(7_000L, first.composeDurationMs)
        assertEquals(2, first.composeCalls)
        assertEquals(40, first.reasoningTokens)
        assertEquals(listOf("Novita", "Parasail"), first.servedProviders)
        assertEquals(4, first.wordCount)
        assertEquals(4.0, first.composeCostCents)
        assertEquals(7.0, first.totalCostCents)
        assertEquals(0.8, first.judgeScore)
        assertNull(throughputResult.runs.first { it.episodeId == 102L }.reasoningTokens)

        assertEquals(2, throughputResult.completedRuns)
        assertEquals(0.7, throughputResult.mean.judgeScore!!, 1e-9)
        assertEquals(5.0, throughputResult.mean.composeCostCents)
        assertEquals(5_000.0, throughputResult.mean.composeDurationMs)
        assertEquals(1.5, throughputResult.mean.composeCalls)
        assertEquals(40.0, throughputResult.mean.reasoningTokens)
        assertEquals(3.0, throughputResult.mean.wordCount)

        val baseline = experiment.variants.first { it.name == "baseline" }
        assertEquals(1, baseline.failedRuns)
        assertEquals(0, baseline.completedRuns)
        assertNull(baseline.mean.judgeScore)
        assertNull(baseline.runs.single().totalCostCents)
    }
}
