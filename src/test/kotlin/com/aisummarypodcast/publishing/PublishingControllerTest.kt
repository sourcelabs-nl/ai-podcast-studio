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
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
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
        every { publishingService.publish(episode, podcast, userId, "soundcloud") } returns
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

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/$episodeId/publish/soundcloud"))
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

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/$episodeId/publish/soundcloud"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `publish returns 400 for unsupported target`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(episodeId) } returns episode
        every { publishingService.publish(episode, podcast, userId, "youtube") } throws
            IllegalArgumentException("Unsupported publish target: youtube")

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/$episodeId/publish/youtube"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Unsupported publish target: youtube"))
    }

    @Test
    fun `publish returns 409 when already published`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(episodeId) } returns episode
        every { publishingService.publish(episode, podcast, userId, "soundcloud") } throws
            IllegalStateException("Episode is already published to soundcloud")

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/$episodeId/publish/soundcloud"))
            .andExpect(status().isConflict)
    }

    @Test
    fun `publish returns 413 with deletion plan when quota full`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(episodeId) } returns episode
        every { publishingService.publish(episode, podcast, userId, "soundcloud") } throws
            SoundCloudQuotaExceededException(
                message = "SoundCloud upload quota is full.",
                plan = QuotaDeletionPlan(
                    tracksToDelete = listOf(QuotaTrackToDelete(id = 100, title = "Old", createdAt = "2026-01-01T00:00:00Z", durationSeconds = 360)),
                    secondsToFree = 540
                )
            )

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/$episodeId/publish/soundcloud"))
            .andExpect(status().isPayloadTooLarge)
            .andExpect(jsonPath("$.code").value("quota_exceeded"))
            .andExpect(jsonPath("$.secondsToFree").value(540))
            .andExpect(jsonPath("$.tracksToDelete[0].id").value(100))
    }

    @Test
    fun `free-and-publish deletes tracks and returns 200`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(episodeId) } returns episode
        every { publishingService.freeQuotaAndPublish(episode, podcast, userId, "soundcloud", listOf(100L, 200L)) } returns
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

        mockMvc.perform(
            post("/users/$userId/podcasts/$podcastId/episodes/$episodeId/publish/soundcloud/free-and-publish")
                .contentType("application/json")
                .content("""{"trackIds":[100,200]}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PUBLISHED"))
    }

    @Test
    fun `free-and-publish returns 413 when still over quota`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(episodeId) } returns episode
        every { publishingService.freeQuotaAndPublish(episode, podcast, userId, "soundcloud", listOf(100L)) } throws
            SoundCloudQuotaExceededException(
                message = "SoundCloud upload quota is full.",
                plan = QuotaDeletionPlan(
                    tracksToDelete = listOf(QuotaTrackToDelete(id = 200, title = "Another", createdAt = "2026-01-02T00:00:00Z", durationSeconds = 360)),
                    secondsToFree = 200
                )
            )

        mockMvc.perform(
            post("/users/$userId/podcasts/$podcastId/episodes/$episodeId/publish/soundcloud/free-and-publish")
                .contentType("application/json")
                .content("""{"trackIds":[100]}""")
        )
            .andExpect(status().isPayloadTooLarge)
            .andExpect(jsonPath("$.tracksToDelete[0].id").value(200))
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
