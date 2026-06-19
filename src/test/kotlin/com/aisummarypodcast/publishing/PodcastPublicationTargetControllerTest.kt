package com.aisummarypodcast.publishing

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.podcast.PodcastService
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastPublicationTarget
import com.aisummarypodcast.store.User
import com.aisummarypodcast.user.UserService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(PodcastPublicationTargetController::class)
class PodcastPublicationTargetControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var userService: UserService

    @MockkBean
    private lateinit var podcastService: PodcastService

    @MockkBean
    private lateinit var targetService: PodcastPublicationTargetService

    @MockkBean(relaxed = true)
    private lateinit var appProperties: AppProperties

    private val userId = "user-1"
    private val podcastId = "pod-1"
    private val user = User(id = userId, name = "Test User")
    private val podcast = Podcast(id = podcastId, userId = userId, name = "Test Pod", topic = "tech")

    @Test
    fun `list returns targets for podcast`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { targetService.list(podcastId) } returns listOf(
            PodcastPublicationTarget(id = 1, podcastId = podcastId, target = "soundcloud", config = """{"playlistId":"123"}""", enabled = true),
            PodcastPublicationTarget(id = 2, podcastId = podcastId, target = "ftp", config = """{"remotePath":"/shows/"}""", enabled = false)
        )

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/publication-targets"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].target").value("soundcloud"))
            .andExpect(jsonPath("$[0].config.playlistId").value("123"))
            .andExpect(jsonPath("$[0].enabled").value(true))
            .andExpect(jsonPath("$[1].target").value("ftp"))
            .andExpect(jsonPath("$[1].enabled").value(false))
    }

    @Test
    fun `list returns empty array for podcast with no targets`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { targetService.list(podcastId) } returns emptyList()

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/publication-targets"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isEmpty)
    }

    @Test
    fun `list returns 404 for unknown podcast`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns null

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/publication-targets"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `upsert creates new target`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { targetService.upsert(podcastId, "ftp", any(), true, false) } returns
            PodcastPublicationTarget(id = 1, podcastId = podcastId, target = "ftp", config = """{"remotePath":"/shows/tech/"}""", enabled = true)

        mockMvc.perform(
            put("/users/$userId/podcasts/$podcastId/publication-targets/ftp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"config":{"remotePath":"/shows/tech/"},"enabled":true}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.target").value("ftp"))
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.config.remotePath").value("/shows/tech/"))
    }

    @Test
    fun `delete returns 204 when target exists`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { targetService.delete(podcastId, "ftp") } returns true

        mockMvc.perform(delete("/users/$userId/podcasts/$podcastId/publication-targets/ftp"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `delete returns 404 when target does not exist`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { targetService.delete(podcastId, "ftp") } returns false

        mockMvc.perform(delete("/users/$userId/podcasts/$podcastId/publication-targets/ftp"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `returns 404 when podcast does not belong to user`() {
        val otherPodcast = podcast.copy(userId = "other-user")
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns otherPodcast

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/publication-targets"))
            .andExpect(status().isNotFound)
    }
}
