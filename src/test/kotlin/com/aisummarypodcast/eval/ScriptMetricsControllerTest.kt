package com.aisummarypodcast.eval

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.podcast.EpisodeService
import com.aisummarypodcast.podcast.PodcastService
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.User
import com.aisummarypodcast.user.UserService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ScriptMetricsController::class)
@Import(ScriptMetricsService::class)
class ScriptMetricsControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var userService: UserService

    @MockkBean
    private lateinit var podcastService: PodcastService

    @MockkBean(relaxed = true)
    private lateinit var episodeService: EpisodeService

    @MockkBean(relaxed = true)
    private lateinit var appProperties: AppProperties

    private val userId = "user-1"
    private val user = User(id = userId, name = "Test User")
    private val podcastId = "podcast-1"
    private val podcast = Podcast(id = podcastId, userId = userId, name = "Test", topic = "tech")

    private val episode = Episode(
        id = 7L,
        podcastId = podcastId,
        generatedAt = "2025-01-01T00:00:00Z",
        scriptText = "<interviewer>One two three.</interviewer><expert>Four five six seven.</expert>",
        status = EpisodeStatus.GENERATED,
        durationSeconds = 120
    )

    @Test
    fun `returns metrics for one episode`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(7L) } returns episode

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes/7/metrics"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.episodeId").value(7))
            .andExpect(jsonPath("$.durationSeconds").value(120))
            .andExpect(jsonPath("$.metrics.turnCount").value(2))
            .andExpect(jsonPath("$.metrics.totalWords").value(7))
    }

    @Test
    fun `an episode of another podcast is not found`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(7L) } returns episode.copy(podcastId = "other")

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes/7/metrics"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `a podcast owned by another user is not found`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast.copy(userId = "someone-else")

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/metrics"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `returns metrics for a range of episodes`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findByPodcastIdPaged(podcastId, emptyList(), any()) } returns
            PageImpl(listOf(episode, episode.copy(id = 8L)))

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/metrics?limit=2"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].episodeId").value(7))
    }

    @Test
    fun `rejects a limit outside the allowed range`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/metrics?limit=0"))
            .andExpect(status().isBadRequest)
    }
}
