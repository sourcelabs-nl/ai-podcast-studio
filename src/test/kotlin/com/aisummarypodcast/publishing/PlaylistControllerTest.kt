package com.aisummarypodcast.publishing

import com.aisummarypodcast.api.InvalidRequestException
import com.aisummarypodcast.api.OperationFailedException
import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.podcast.PodcastService
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.User
import com.aisummarypodcast.user.UserService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(PlaylistController::class)
class PlaylistControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @MockkBean private lateinit var userService: UserService
    @MockkBean private lateinit var podcastService: PodcastService
    @MockkBean private lateinit var publishingService: PublishingService

    // WebConfig sits in the sliced context and needs it; the controller itself does not use it.
    @MockkBean(relaxed = true) private lateinit var appProperties: AppProperties

    private val podcast = Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech")
    private val url = "/users/u1/podcasts/p1/playlist/rebuild"

    @BeforeEach
    fun setUp() {
        every { userService.findById("u1") } returns User(id = "u1", name = "Test")
        every { podcastService.findById("p1") } returns podcast
    }

    @Test
    fun `rebuild returns the track ids`() {
        every { publishingService.rebuildSoundCloudPlaylist(podcast, "u1") } returns listOf(1L, 2L)

        mockMvc.perform(post(url))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"trackIds":[1,2]}""", true))
    }

    @Test
    fun `rebuild answers 400 when the request is not valid`() {
        every { publishingService.rebuildSoundCloudPlaylist(podcast, "u1") } throws
            InvalidRequestException("No published SoundCloud tracks found for this podcast")

        mockMvc.perform(post(url))
            .andExpect(status().isBadRequest)
            .andExpect(content().json("""{"error":"No published SoundCloud tracks found for this podcast"}""", true))
    }

    @Test
    fun `rebuild answers 500 when the rebuild fails`() {
        every { publishingService.rebuildSoundCloudPlaylist(podcast, "u1") } throws
            OperationFailedException("Failed to rebuild playlist: boom", RuntimeException("boom"))

        mockMvc.perform(post(url))
            .andExpect(status().isInternalServerError)
            .andExpect(content().json("""{"error":"Failed to rebuild playlist: boom"}""", true))
    }
}
