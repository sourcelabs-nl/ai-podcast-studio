package com.aisummarypodcast.eval

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.EvalProperties
import com.aisummarypodcast.config.JudgeMode
import com.aisummarypodcast.config.JudgeProperties
import com.aisummarypodcast.llm.ModelResolver
import com.aisummarypodcast.llm.PipelineStage
import com.aisummarypodcast.llm.ResolvedModel
import com.aisummarypodcast.llm.TokenUsage
import com.aisummarypodcast.podcast.EpisodeService
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeScore
import com.aisummarypodcast.store.EpisodeScoreRepository
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EpisodeScoringServiceTest {

    private val scriptJudge = mockk<ScriptJudge>()
    private val episodeService = mockk<EpisodeService>()
    private val podcastRepository = mockk<PodcastRepository>()
    private val repository = mockk<EpisodeScoreRepository>()
    private val modelResolver = mockk<ModelResolver>()
    private val appProperties = mockk<AppProperties>()

    private val podcast = Podcast(id = "podcast-1", userId = "user-1", name = "Test", topic = "tech")
    private val episode = Episode(
        id = 7L,
        podcastId = "podcast-1",
        generatedAt = "2026-09-15T00:00:00Z",
        scriptText = "<interviewer>Hello there.</interviewer><expert>Hello back.</expert>"
    )

    private fun serviceWith(mode: JudgeMode, norm: Double? = null): EpisodeScoringService {
        every { appProperties.eval } returns EvalProperties(JudgeProperties(mode, norm))
        return EpisodeScoringService(
            scriptJudge, episodeService, podcastRepository, repository,
            modelResolver, JsonMapper.builder().build(), appProperties
        )
    }

    private fun stubJudgeReturning(anchors: ScriptJudgeAnchors) {
        every { podcastRepository.findById("podcast-1") } returns java.util.Optional.of(podcast)
        every { repository.findByEpisodeIdAndScorerVersion(7L, any()) } returns null
        every { modelResolver.resolve(podcast, PipelineStage.EVAL) } returns
            ResolvedModel("openrouter", "judge-model", null, PipelineStage.EVAL)
        coEvery { scriptJudge.judge(any(), "user-1", any(), 7L) } returns
            ScriptJudgement(anchors, TokenUsage(100, 20), 1)
        every { repository.save(any<EpisodeScore>()) } answers { firstArg() }
    }

    /** A perfect script, so a norm below 1.0 is comfortably met. */
    private val strongAnchors = ScriptJudgeAnchors(
        promises = listOf(PromiseAnchor(1, 30), PromiseAnchor(2, 40)),
        humorBeats = (1..6).map { HumorAnchor(it, if (it % 2 == 0) "expert" else "interviewer", true) },
        teaserTopics = listOf("one", "two", "three")
    )

    /** An empty script structurally: no device the rules ask for is present. */
    private val weakAnchors = ScriptJudgeAnchors()

    @Test
    fun `off makes no judge call and writes no row`() = runTest {
        val service = serviceWith(JudgeMode.OFF)

        assertNull(service.scoreEpisode(episode))

        coVerify(exactly = 0) { scriptJudge.judge(any(), any(), any()) }
        coVerify(exactly = 0) { repository.save(any<EpisodeScore>()) }
    }

    @Test
    fun `advise persists the score and never reports it below a norm`() = runTest {
        stubJudgeReturning(weakAnchors)
        val service = serviceWith(JudgeMode.ADVISE)

        val outcome = service.scoreEpisode(episode)!!

        assertEquals(0.0, outcome.score.overall)
        assertFalse(outcome.decision.belowNorm)
        assertEquals(JudgeMode.ADVISE, outcome.decision.mode)
        coVerify(exactly = 1) { repository.save(any<EpisodeScore>()) }
    }

    @Test
    fun `enforce without a norm behaves as advise and says so`() = runTest {
        stubJudgeReturning(weakAnchors)
        val service = serviceWith(JudgeMode.ENFORCE, norm = null)

        val outcome = service.scoreEpisode(episode)!!

        // The worst possible score, and still nothing is acted on: there is nothing to act against.
        assertEquals(0.0, outcome.score.overall)
        assertFalse(outcome.decision.belowNorm)
        assertNull(outcome.decision.norm)
        assertEquals(JudgeMode.ENFORCE, outcome.decision.mode)
        assertTrue(outcome.decision.reason.contains("no norm"))
    }

    @Test
    fun `enforce with a norm reports a score below it`() = runTest {
        stubJudgeReturning(weakAnchors)
        val service = serviceWith(JudgeMode.ENFORCE, norm = 0.6)

        val outcome = service.scoreEpisode(episode)!!

        assertTrue(outcome.decision.belowNorm)
        assertEquals(0.6, outcome.decision.norm)
        assertTrue(outcome.decision.reason.contains("below"))
    }

    @Test
    fun `enforce with a norm passes a score that meets it`() = runTest {
        stubJudgeReturning(strongAnchors)
        val service = serviceWith(JudgeMode.ENFORCE, norm = 0.6)

        val outcome = service.scoreEpisode(episode)!!

        assertEquals(1.0, outcome.score.overall)
        assertFalse(outcome.decision.belowNorm)
    }

    @Test
    fun `an episode already scored at this version is not judged again`() = runTest {
        val existing = mockk<EpisodeScore>(relaxed = true)
        every { existing.overall } returns 0.9
        every { podcastRepository.findById(any()) } returns java.util.Optional.of(podcast)
        every { repository.findByEpisodeIdAndScorerVersion(7L, EpisodeScoringService.SCORER_VERSION) } returns existing
        val service = serviceWith(JudgeMode.ADVISE)

        val outcome = service.scoreEpisode(episode)!!

        assertEquals(existing, outcome.score)
        coVerify(exactly = 0) { scriptJudge.judge(any(), any(), any()) }
    }

    @Test
    fun `the stored row carries the judge model and its own cost`() = runTest {
        stubJudgeReturning(strongAnchors)
        val saved = slot<EpisodeScore>()
        every { repository.save(capture(saved)) } answers { firstArg() }
        val service = serviceWith(JudgeMode.ADVISE)

        service.scoreEpisode(episode)

        assertEquals("judge-model", saved.captured.judgeModel)
        assertEquals(EpisodeScoringService.SCORER_VERSION, saved.captured.scorerVersion)
        assertEquals(100, saved.captured.inputTokens)
        assertEquals(1, saved.captured.costCents)
        // The anchors survive so a stored score can be reopened against the script later.
        assertTrue(saved.captured.anchorsJson.contains("promiseTurn"))
    }

    @Test
    fun `a script the judge cannot answer for is skipped, not fatal`() = runTest {
        stubJudgeReturning(weakAnchors)
        coEvery { scriptJudge.judge(any(), any(), any(), 7L) } throws
            IllegalStateException("Judge returned no parseable anchors")
        val service = serviceWith(JudgeMode.ADVISE)

        // Scoring runs over the whole archive at once, so one unreadable script must not throw away
        // every episode after it along with the calls already paid for.
        assertNull(service.scoreEpisode(episode))

        coVerify(exactly = 0) { repository.save(any<EpisodeScore>()) }
    }

    @Test
    fun `a script with no turns is skipped rather than judged`() = runTest {
        every { podcastRepository.findById(any()) } returns java.util.Optional.of(podcast)
        every { repository.findByEpisodeIdAndScorerVersion(any(), any()) } returns null
        val service = serviceWith(JudgeMode.ADVISE)

        assertNull(service.scoreEpisode(episode.copy(scriptText = "")))

        coVerify(exactly = 0) { scriptJudge.judge(any(), any(), any()) }
    }
}
