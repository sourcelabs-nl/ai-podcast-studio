package com.aisummarypodcast.eval

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.JudgeMode
import com.aisummarypodcast.podcast.EpisodeService
import com.aisummarypodcast.podcast.PodcastService
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeScore
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.EvaluationRun
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.User
import com.aisummarypodcast.user.UserService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.RequestBuilder
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(EpisodeScoringController::class)
class EpisodeScoringControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var userService: UserService

    @MockkBean
    private lateinit var podcastService: PodcastService

    @MockkBean(relaxed = true)
    private lateinit var episodeService: EpisodeService

    @MockkBean
    private lateinit var episodeScoringService: EpisodeScoringService

    @MockkBean(relaxed = true)
    private lateinit var evaluationRunRecorder: EvaluationRunRecorder

    @MockkBean(relaxed = true)
    private lateinit var appProperties: AppProperties

    private val userId = "user-1"
    private val user = User(id = userId, name = "Test User")
    private val podcastId = "podcast-1"
    private val podcast = Podcast(id = podcastId, userId = userId, name = "Test", topic = "tech")

    private val episode = Episode(
        id = 7L,
        podcastId = podcastId,
        generatedAt = "2026-09-15T00:00:00Z",
        scriptText = "<interviewer>One.</interviewer><expert>Two.</expert>",
        status = EpisodeStatus.GENERATED
    )

    private val score = EpisodeScore(
        id = 1L, episodeId = 7L, scorerVersion = 1, judgeModel = "judge-model",
        scoredAt = "2026-09-15T00:00:00Z", overall = 0.75, cliffhangerScore = 1.0,
        humorScore = 0.5, teaserScore = 0.75, promises = 2, deferredPromises = 2,
        unpaidPromises = 0, medianDeferralTurns = 29, humorBeats = 6,
        humorSpeakerBalance = 0.5, humorReactionRatio = 1.0, teaserTopics = 3,
        anchorsJson = "{}", inputTokens = 100, outputTokens = 20, costCents = 1
    )

    private val run = EvaluationRun(
        id = 1L, episodeId = 7L, podcastId = podcastId, ranAt = "2026-09-15T10:00:00Z",
        promptHash = "hash1", varietySelection = "openingStyle=SCENE_SET", composeModel = "test-model",
        temperature = 0.8, cacheBypassed = true, cacheHit = false, toolsFiredJson = "{}"
    )

    /**
     * Performs a request against a suspending endpoint.
     *
     * Spring MVC runs a suspend handler asynchronously, so the first exchange only reports that the
     * async request started; the real status arrives on the dispatch that follows.
     */
    private fun performAsync(request: RequestBuilder): ResultActions =
        mockMvc.perform(asyncDispatch(mockMvc.perform(request).andReturn()))

    private fun owns() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
    }

    @Test
    fun `scores one episode`() {
        owns()
        every { episodeService.findById(7L) } returns episode
        coEvery { episodeScoringService.scoreEpisode(episode) } returns
            ScoringOutcome(score, JudgeDecision(JudgeMode.ADVISE, null, false, "advisory only"))

        performAsync(post("/users/$userId/podcasts/$podcastId/episodes/7/scores"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.score.overall").value(0.75))
            .andExpect(jsonPath("$.decision.mode").value("ADVISE"))
    }

    @Test
    fun `a judge that is off returns no content rather than an empty score`() {
        owns()
        every { episodeService.findById(7L) } returns episode
        coEvery { episodeScoringService.scoreEpisode(episode) } returns null

        performAsync(post("/users/$userId/podcasts/$podcastId/episodes/7/scores"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `reads stored scores back`() {
        owns()
        every { episodeService.findById(7L) } returns episode
        every { episodeScoringService.existingScores(7L) } returns listOf(score)

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes/7/scores"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].judgeModel").value("judge-model"))
    }

    @Test
    fun `reads the evaluation runs of a podcast back`() {
        owns()
        every { evaluationRunRecorder.runsForPodcast(podcastId) } returns listOf(run)

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/evaluation-runs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].promptHash").value("hash1"))
            .andExpect(jsonPath("$[0].cacheBypassed").value(true))
    }

    @Test
    fun `evaluation runs of a podcast owned by another user are not found`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast.copy(userId = "someone-else")

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/evaluation-runs"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `an episode of another podcast is not found`() {
        owns()
        every { episodeService.findById(7L) } returns episode.copy(podcastId = "other")

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes/7/scores"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `a podcast owned by another user is not found`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast.copy(userId = "someone-else")

        performAsync(post("/users/$userId/podcasts/$podcastId/scores"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `rejects a limit outside the allowed range`() {
        owns()

        performAsync(post("/users/$userId/podcasts/$podcastId/scores?limit=0"))
            .andExpect(status().isBadRequest)
    }
}
