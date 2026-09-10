package com.aisummarypodcast.publishing

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.PublishingProperties
import com.aisummarypodcast.podcast.EpisodeService
import com.aisummarypodcast.podcast.PodcastEvent
import com.aisummarypodcast.podcast.PodcastService
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodePublication
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastPublicationTarget
import com.aisummarypodcast.store.PublicationStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class AutoPublishListenerTest {

    private val podcastService = mockk<PodcastService>()
    private val episodeService = mockk<EpisodeService>()
    private val targetService = mockk<PodcastPublicationTargetService>()
    private val publishingService = mockk<PublishingService>()
    private val appProperties = mockk<AppProperties> {
        every { publishing } returns PublishingProperties(minArticles = 5)
    }
    private val listener =
        AutoPublishListener(podcastService, episodeService, targetService, publishingService, appProperties)

    private val podcast = Podcast(id = "pod1", userId = "user1", name = "Test Pod", topic = "tech")
    private val episode = Episode(
        id = 1L,
        podcastId = "pod1",
        generatedAt = "2026-02-13T10:00:00Z",
        scriptText = "Test script",
        status = EpisodeStatus.GENERATED,
        audioFilePath = "/tmp/test.mp3"
    )
    private val publication = EpisodePublication(
        id = 10L, episodeId = 1L, target = "ftp",
        status = PublicationStatus.PUBLISHED, createdAt = "2026-02-13T10:00:00Z"
    )

    @Test
    fun `publishes only enabled auto-publish targets on episode generated`() {
        every { targetService.list("pod1") } returns listOf(
            PodcastPublicationTarget(podcastId = "pod1", target = "ftp", enabled = true, autoPublish = true),
            PodcastPublicationTarget(podcastId = "pod1", target = "soundcloud", enabled = true, autoPublish = false)
        )
        every { podcastService.findById("pod1") } returns podcast
        every { episodeService.findById(1L) } returns episode
        every { episodeService.countArticles(1L) } returns 12
        coEvery { publishingService.publish(episode, podcast, "user1", "ftp") } returns publication

        listener.onEpisodeGenerated(PodcastEvent(this, "pod1", "episode", 1L, "episode.generated"))

        coVerify(timeout = 2000) { publishingService.publish(episode, podcast, "user1", "ftp") }
        coVerify(exactly = 0) { publishingService.publish(any(), any(), any(), "soundcloud") }
    }

    @Test
    fun `ignores events other than episode generated`() {
        listener.onEpisodeGenerated(PodcastEvent(this, "pod1", "episode", 1L, "episode.published"))

        verify(exactly = 0) { targetService.list(any()) }
    }

    @Test
    fun `does nothing when no target opts into auto-publish`() {
        every { targetService.list("pod1") } returns listOf(
            PodcastPublicationTarget(podcastId = "pod1", target = "ftp", enabled = true, autoPublish = false)
        )

        listener.onEpisodeGenerated(PodcastEvent(this, "pod1", "episode", 1L, "episode.generated"))

        coVerify(exactly = 0) { publishingService.publish(any(), any(), any(), any()) }
    }

    @Test
    fun `does not auto-publish an episode below the article floor`() {
        every { targetService.list("pod1") } returns listOf(
            PodcastPublicationTarget(podcastId = "pod1", target = "ftp", enabled = true, autoPublish = true)
        )
        every { podcastService.findById("pod1") } returns podcast
        every { episodeService.findById(1L) } returns episode
        every { episodeService.countArticles(1L) } returns 1

        listener.onEpisodeGenerated(PodcastEvent(this, "pod1", "episode", 1L, "episode.generated"))

        coVerify(exactly = 0) { publishingService.publish(any(), any(), any(), any()) }
    }

    @Test
    fun `auto-publishes an episode exactly at the article floor`() {
        every { targetService.list("pod1") } returns listOf(
            PodcastPublicationTarget(podcastId = "pod1", target = "ftp", enabled = true, autoPublish = true)
        )
        every { podcastService.findById("pod1") } returns podcast
        every { episodeService.findById(1L) } returns episode
        every { episodeService.countArticles(1L) } returns 5
        coEvery { publishingService.publish(episode, podcast, "user1", "ftp") } returns publication

        listener.onEpisodeGenerated(PodcastEvent(this, "pod1", "episode", 1L, "episode.generated"))

        coVerify(timeout = 2000) { publishingService.publish(episode, podcast, "user1", "ftp") }
    }
}
