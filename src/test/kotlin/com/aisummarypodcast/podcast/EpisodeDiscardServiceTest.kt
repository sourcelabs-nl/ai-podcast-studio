package com.aisummarypodcast.podcast

import com.aisummarypodcast.publishing.PublishingService
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeStatus
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EpisodeDiscardServiceTest {

    private val episodeService = mockk<EpisodeService>()
    private val publishingService = mockk<PublishingService>()

    private val episodeDiscardService = EpisodeDiscardService(episodeService, publishingService)

    @Test
    fun `discard refuses an episode that is published to a target`() {
        val episode = Episode(id = 1L, podcastId = "p1", generatedAt = "now", scriptText = "Script", status = EpisodeStatus.GENERATED)
        every { publishingService.liveTargets(1L) } returns listOf("ftp")

        val error = assertThrows<EpisodePublishedException> {
            episodeDiscardService.discard(episode, "p1")
        }

        assertEquals("Episode is published to ftp; unpublish it first", error.message)
        verify(exactly = 0) { episodeService.discardOnly(any(), any()) }
        verify(exactly = 0) { episodeService.discardAndResetArticles(any(), any()) }
    }

    @Test
    fun `discard proceeds when every publication is unpublished or failed`() {
        val episode = Episode(id = 1L, podcastId = "p1", generatedAt = "now", scriptText = "Script", status = EpisodeStatus.FAILED)
        every { publishingService.liveTargets(1L) } returns emptyList()
        justRun { episodeService.discardOnly(episode, "p1") }

        episodeDiscardService.discard(episode, "p1")

        verify { episodeService.discardOnly(episode, "p1") }
        verify(exactly = 0) { episodeService.discardAndResetArticles(any(), any()) }
    }

    @Test
    fun `discard resets articles for a non-failed episode`() {
        val episode = Episode(id = 1L, podcastId = "p1", generatedAt = "now", scriptText = "Script", status = EpisodeStatus.PENDING_REVIEW)
        every { publishingService.liveTargets(1L) } returns emptyList()
        justRun { episodeService.discardAndResetArticles(episode, "p1") }

        episodeDiscardService.discard(episode, "p1")

        verify { episodeService.discardAndResetArticles(episode, "p1") }
        verify(exactly = 0) { episodeService.discardOnly(any(), any()) }
    }
}
