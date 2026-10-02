package com.aisummarypodcast.podcast

import com.aisummarypodcast.api.ResourceNotFoundException
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.User
import com.aisummarypodcast.user.UserService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PodcastAccessTest {

    private val userService = mockk<UserService>()
    private val podcastService = mockk<PodcastService>()
    private val episodeService = mockk<EpisodeService>()
    private val access = PodcastAccess(userService, podcastService, episodeService)

    private val userId = "user-1"
    private val podcastId = "podcast-1"
    private val episodeId = 7L
    private val podcast = Podcast(id = podcastId, userId = userId, name = "Test", topic = "tech")
    private val episode = Episode(id = episodeId, podcastId = podcastId, generatedAt = "2026-01-01T00:00:00Z", scriptText = "script")

    @Test
    fun `podcast is returned for its owner`() {
        every { userService.findById(userId) } returns User(id = userId, name = "Test User")
        every { podcastService.findById(podcastId) } returns podcast

        assertEquals(podcast, access.podcast(userId, podcastId))
    }

    @Test
    fun `unknown user is not found`() {
        every { userService.findById(userId) } returns null

        assertThrows(ResourceNotFoundException::class.java) { access.podcast(userId, podcastId) }
    }

    @Test
    fun `unknown podcast is not found`() {
        every { userService.findById(userId) } returns User(id = userId, name = "Test User")
        every { podcastService.findById(podcastId) } returns null

        assertThrows(ResourceNotFoundException::class.java) { access.podcast(userId, podcastId) }
    }

    @Test
    fun `podcast of another user is not found`() {
        every { userService.findById(userId) } returns User(id = userId, name = "Test User")
        every { podcastService.findById(podcastId) } returns podcast.copy(userId = "someone-else")

        assertThrows(ResourceNotFoundException::class.java) { access.podcast(userId, podcastId) }
    }

    @Test
    fun `episode is returned for the owner of its podcast`() {
        every { userService.findById(userId) } returns User(id = userId, name = "Test User")
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(episodeId) } returns episode

        assertEquals(episode, access.episode(userId, podcastId, episodeId))
    }

    @Test
    fun `episode of a podcast of another user is not found`() {
        every { userService.findById(userId) } returns User(id = userId, name = "Test User")
        every { podcastService.findById(podcastId) } returns podcast.copy(userId = "someone-else")
        every { episodeService.findById(episodeId) } returns episode

        assertThrows(ResourceNotFoundException::class.java) { access.episode(userId, podcastId, episodeId) }
    }

    @Test
    fun `unknown episode is not found`() {
        every { userService.findById(userId) } returns User(id = userId, name = "Test User")
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(episodeId) } returns null

        assertThrows(ResourceNotFoundException::class.java) { access.episode(userId, podcastId, episodeId) }
    }

    @Test
    fun `episode of another podcast is not found`() {
        every { userService.findById(userId) } returns User(id = userId, name = "Test User")
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(episodeId) } returns episode.copy(podcastId = "other-podcast")

        assertThrows(ResourceNotFoundException::class.java) { access.episode(userId, podcastId, episodeId) }
    }
}
