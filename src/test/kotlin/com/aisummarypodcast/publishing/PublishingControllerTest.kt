package com.aisummarypodcast.publishing

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.podcast.EpisodeService
import com.aisummarypodcast.podcast.PodcastService
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodePublication
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PublicationStatus
import com.aisummarypodcast.store.User
import com.aisummarypodcast.user.UserService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(PublishingController::class)
class PublishingControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var userService: UserService

    @MockkBean
    private lateinit var podcastService: PodcastService

    @MockkBean(relaxed = true)
    private lateinit var episodeService: EpisodeService

    @MockkBean
    private lateinit var publishingService: PublishingService

    @MockkBean(relaxed = true)
    private lateinit var appProperties: AppProperties

    private val userId = "user-1"
    private val podcastId = "pod-1"
    private val episodeId = 1L
    private val user = User(id = userId, name = "Test User")
    private val podcast = Podcast(id = podcastId, userId = userId, name = "Test Pod", topic = "tech")
    private val episode = Episode(
        id = episodeId,
        podcastId = podcastId,
        generatedAt = "2026-02-13T10:00:00Z",
        scriptText = "Test script",
        status = EpisodeStatus.GENERATED,
        audioFilePath = "/tmp/test.mp3"
    )

    @Test
    fun `publish returns 200 on success`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(episodeId) } returns episode
        coEvery { publishingService.publish(episode, podcast, userId, "soundcloud") } returns
            EpisodePublication(
                id = 10L,
                episodeId = episodeId,
                target = "soundcloud",
                status = PublicationStatus.PUBLISHED,
                externalId = "sc-123",
                externalUrl = "https://soundcloud.com/track/123",
                publishedAt = "2026-02-13T10:00:00Z",
                createdAt = "2026-02-13T10:00:00Z"
            )

        val mvcResult = mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/$episodeId/publish/soundcloud"))
            .andReturn()
        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.target").value("soundcloud"))
            .andExpect(jsonPath("$.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.externalId").value("sc-123"))
    }

    @Test
    fun `publish returns 404 for unknown episode`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(episodeId) } returns null

        val mvcResult = mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/$episodeId/publish/soundcloud"))
            .andReturn()
        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `publish returns 400 for unsupported target`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(episodeId) } returns episode
        coEvery { publishingService.publish(episode, podcast, userId, "youtube") } throws
            IllegalArgumentException("Unsupported publish target: youtube")

        val mvcResult = mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/$episodeId/publish/youtube"))
            .andReturn()
        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Unsupported publish target: youtube"))
    }

    @Test
    fun `publish returns 409 when already published`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(episodeId) } returns episode
        coEvery { publishingService.publish(episode, podcast, userId, "soundcloud") } throws
            AlreadyPublishedException("Episode is already published to soundcloud")

        val mvcResult = mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/$episodeId/publish/soundcloud"))
            .andReturn()
        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isConflict)
    }

    @Test
    fun `list publications returns empty array`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(episodeId) } returns episode
        every { publishingService.getPublications(episodeId) } returns emptyList()

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes/$episodeId/publications"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isEmpty)
    }

    @Test
    fun `list publications returns existing publications`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(episodeId) } returns episode
        every { publishingService.getPublications(episodeId) } returns listOf(
            EpisodePublication(
                id = 10L,
                episodeId = episodeId,
                target = "soundcloud",
                status = PublicationStatus.PUBLISHED,
                externalId = "sc-123",
                externalUrl = "https://soundcloud.com/track/123",
                publishedAt = "2026-02-13T10:00:00Z",
                createdAt = "2026-02-13T10:00:00Z"
            )
        )

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes/$episodeId/publications"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].target").value("soundcloud"))
            .andExpect(jsonPath("$[0].status").value("PUBLISHED"))
            .andExpect(jsonPath("$[0].externalId").value("sc-123"))
    }
}
