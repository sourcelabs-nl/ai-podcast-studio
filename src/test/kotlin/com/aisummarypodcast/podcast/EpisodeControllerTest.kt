package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.User
import com.aisummarypodcast.user.UserService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType

import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(EpisodeController::class)
class EpisodeControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var podcastService: PodcastService

    @MockkBean
    private lateinit var userService: UserService

    @MockkBean(relaxed = true)
    private lateinit var episodeService: EpisodeService

    @MockkBean(relaxed = true)
    private lateinit var appProperties: AppProperties

    private val userId = "user-1"
    private val user = User(id = userId, name = "Test User")
    private val podcastId = "podcast-1"
    private val podcast = Podcast(id = podcastId, userId = userId, name = "Test", topic = "tech")

    private val pendingEpisode = Episode(
        id = 1L, podcastId = podcastId, generatedAt = "2025-01-01T00:00:00Z",
        scriptText = "Test script", status = EpisodeStatus.PENDING_REVIEW
    )

    private val generatedEpisode = Episode(
        id = 2L, podcastId = podcastId, generatedAt = "2025-01-01T00:00:00Z",
        scriptText = "Test script", status = EpisodeStatus.GENERATED,
        audioFilePath = "/audio/test.mp3", durationSeconds = 120
    )

    @Test
    fun `list episodes returns paged envelope with all episodes`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findByPodcastIdPaged(podcastId, emptyList(), any()) } returns
            org.springframework.data.domain.PageImpl(listOf(pendingEpisode, generatedEpisode))

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.page").value(0))
    }

    @Test
    fun `list episodes with status filter`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every {
            episodeService.findByPodcastIdPaged(podcastId, listOf(EpisodeStatus.PENDING_REVIEW), any())
        } returns org.springframework.data.domain.PageImpl(listOf(pendingEpisode))

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes?status=PENDING_REVIEW"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].status").value("PENDING_REVIEW"))
    }

    @Test
    fun `list episodes accepts multiple status values`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every {
            episodeService.findByPodcastIdPaged(
                podcastId,
                listOf(EpisodeStatus.PENDING_REVIEW, EpisodeStatus.GENERATED),
                any()
            )
        } returns org.springframework.data.domain.PageImpl(listOf(pendingEpisode, generatedEpisode))

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes?status=PENDING_REVIEW&status=GENERATED"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
    }

    @Test
    fun `list episodes rejects negative page`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes?page=-1"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `list episodes rejects pageSize above max`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes?pageSize=500"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `list episodes for non-existing podcast returns 404`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns null

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `get single episode`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(1L) } returns pendingEpisode

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes/1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.scriptText").value("Test script"))
            .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
    }

    @Test
    fun `get non-existing episode returns 404`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(99L) } returns null

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes/99"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `edit script of pending episode`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(1L) } returns pendingEpisode
        every { episodeService.updateScript(pendingEpisode, "Updated script") } returns pendingEpisode.copy(scriptText = "Updated script")

        mockMvc.perform(
            put("/users/$userId/podcasts/$podcastId/episodes/1/script")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"scriptText":"Updated script"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.scriptText").value("Updated script"))
    }

    @Test
    fun `edit script of non-pending episode returns 409`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(2L) } returns generatedEpisode

        mockMvc.perform(
            put("/users/$userId/podcasts/$podcastId/episodes/2/script")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"scriptText":"Updated script"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").exists())
    }

    @Test
    fun `approve pending episode returns 202`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(1L) } returns pendingEpisode
        justRun { episodeService.approveAndGenerateAudio(pendingEpisode, podcast) }

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/1/approve"))
            .andExpect(status().isAccepted)

        verify { episodeService.approveAndGenerateAudio(pendingEpisode, podcast) }
    }

    @Test
    fun `approve failed episode returns 202`() {
        val failedEpisode = pendingEpisode.copy(status = EpisodeStatus.FAILED)
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(1L) } returns failedEpisode
        justRun { episodeService.approveAndGenerateAudio(failedEpisode, podcast) }

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/1/approve"))
            .andExpect(status().isAccepted)
    }

    @Test
    fun `approve generated episode returns 409`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(2L) } returns generatedEpisode

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/2/approve"))
            .andExpect(status().isConflict)
    }

    @Test
    fun `approve publication of generated episode returns 200`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(2L) } returns generatedEpisode
        every { episodeService.approveForPublication(generatedEpisode, podcast) } returns generatedEpisode.copy(publishApproved = true)

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/2/approve-publication"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.publishApproved").value(true))

        verify { episodeService.approveForPublication(generatedEpisode, podcast) }
    }

    @Test
    fun `approve publication of pending episode returns 409`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(1L) } returns pendingEpisode

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/1/approve-publication"))
            .andExpect(status().isConflict)
    }

    @Test
    fun `discard pending episode delegates to service`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(1L) } returns pendingEpisode
        justRun { episodeService.discardAndResetArticles(pendingEpisode, podcastId) }

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/1/discard"))
            .andExpect(status().isOk)

        verify { episodeService.discardAndResetArticles(pendingEpisode, podcastId) }
    }

    @Test
    fun `discard failed episode calls discardOnly`() {
        val failedEpisode = Episode(
            id = 5L, podcastId = podcastId, generatedAt = "2025-01-01T00:00:00Z",
            scriptText = "", status = EpisodeStatus.FAILED, errorMessage = "TTS failure"
        )
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(5L) } returns failedEpisode
        justRun { episodeService.discardOnly(failedEpisode, podcastId) }

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/5/discard"))
            .andExpect(status().isOk)

        verify { episodeService.discardOnly(failedEpisode, podcastId) }
        verify(exactly = 0) { episodeService.discardAndResetArticles(any(), any()) }
    }

    @Test
    fun `discard non-discardable episode returns 409`() {
        val approvedEpisode = Episode(
            id = 3L, podcastId = podcastId, generatedAt = "2025-01-01T00:00:00Z",
            scriptText = "Test script", status = EpisodeStatus.APPROVED
        )
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(3L) } returns approvedEpisode

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/3/discard"))
            .andExpect(status().isConflict)
    }

    @Test
    fun `discard GENERATING_AUDIO episode returns 409 with audio message`() {
        val generatingAudioEpisode = Episode(
            id = 4L, podcastId = podcastId, generatedAt = "2025-01-01T00:00:00Z",
            scriptText = "Test script", status = EpisodeStatus.GENERATING_AUDIO
        )
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(4L) } returns generatingAudioEpisode

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/4/discard"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("Audio generation is in progress"))
    }

    // --- regenerate-recap tests ---

    @Test
    fun `regenerate-recap returns updated episode`() {
        val episodeWithRecap = generatedEpisode.copy(recap = "New recap.", showNotes = "New recap.")
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(2L) } returns generatedEpisode
        every { episodeService.regenerateRecap(generatedEpisode, podcast) } returns episodeWithRecap

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/2/regenerate-recap"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.recap").value("New recap."))
            .andExpect(jsonPath("$.showNotes").value("New recap."))

        verify { episodeService.regenerateRecap(generatedEpisode, podcast) }
    }

    @Test
    fun `regenerate-recap for non-existing episode returns 404`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(99L) } returns null

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/99/regenerate-recap"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `regenerate-recap for wrong podcast returns 404`() {
        val otherPodcastEpisode = generatedEpisode.copy(podcastId = "other-podcast")
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(2L) } returns otherPodcastEpisode

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/2/regenerate-recap"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `regenerate-recap returns 500 on failure`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(2L) } returns generatedEpisode
        every { episodeService.regenerateRecap(generatedEpisode, podcast) } throws RuntimeException("LLM error")

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/2/regenerate-recap"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.error").exists())
    }

    @Test
    fun `articles for non-existing episode returns 404`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(99L) } returns null

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes/99/articles"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `articles for episode in wrong podcast returns 404`() {
        val otherPodcastEpisode = pendingEpisode.copy(podcastId = "other-podcast")
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(1L) } returns otherPodcastEpisode

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes/1/articles"))
            .andExpect(status().isNotFound)
    }
}
