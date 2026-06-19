package com.aisummarypodcast.publishing

import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodePublication
import com.aisummarypodcast.store.EpisodePublicationRepository
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastPublicationTarget
import com.aisummarypodcast.store.PublicationStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PublishingServiceTest {

    private val publisher = mockk<EpisodePublisher> {
        every { targetName() } returns "soundcloud"
        every { postPublish(any(), any()) } returns Unit
    }
    private val registry = PublisherRegistry(listOf(publisher))
    private val publicationRepository = mockk<EpisodePublicationRepository>(relaxed = true)
    private val episodeRepository = mockk<com.aisummarypodcast.store.EpisodeRepository>()
    private val soundCloudPublisher = mockk<SoundCloudPublisher>()
    private val targetService = mockk<PodcastPublicationTargetService>()
    private val staticFeedExporter = mockk<com.aisummarypodcast.podcast.StaticFeedExporter>(relaxed = true)
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val service = PublishingService(registry, publicationRepository, episodeRepository, soundCloudPublisher, targetService, staticFeedExporter, eventPublisher)

    private val podcast = Podcast(id = "pod1", userId = "user1", name = "Test Pod", topic = "tech")
    private val episode = Episode(
        id = 1L,
        podcastId = "pod1",
        generatedAt = "2026-02-13T10:00:00Z",
        scriptText = "Test script",
        status = EpisodeStatus.GENERATED,
        audioFilePath = "/tmp/test.mp3"
    )

    private val enabledTarget = PodcastPublicationTarget(id = 1, podcastId = "pod1", target = "soundcloud", config = "{}", enabled = true)

    @Test
    fun `publish succeeds for valid episode`() {
        every { targetService.get("pod1", "soundcloud") } returns enabledTarget
        every { publicationRepository.findByEpisodeIdAndTarget(1L, "soundcloud") } returns null
        every { publicationRepository.save(any()) } answers { firstArg<EpisodePublication>().copy(id = 10L) }
        every { publisher.publish(episode, podcast, "user1") } returns PublishResult("sc-123", "https://soundcloud.com/track/123")

        val result = service.publish(episode, podcast, "user1", "soundcloud")

        assertEquals(PublicationStatus.PUBLISHED, result.status)
        assertEquals("sc-123", result.externalId)
    }

    @Test
    fun `publish throws for unknown target`() {
        assertThrows<IllegalArgumentException> {
            service.publish(episode, podcast, "user1", "youtube")
        }
    }

    @Test
    fun `publish throws when episode not generated`() {
        every { targetService.get("pod1", "soundcloud") } returns enabledTarget
        val pendingEpisode = episode.copy(status = EpisodeStatus.PENDING_REVIEW)

        assertThrows<IllegalStateException> {
            service.publish(pendingEpisode, podcast, "user1", "soundcloud")
        }
    }

    @Test
    fun `publish throws when approval required and episode not approved`() {
        every { targetService.get("pod1", "soundcloud") } returns enabledTarget
        val approvalPodcast = podcast.copy(requirePublishApproval = true)
        val unapproved = episode.copy(publishApproved = false)

        val ex = assertThrows<IllegalStateException> {
            service.publish(unapproved, approvalPodcast, "user1", "soundcloud")
        }
        assertTrue(ex.message!!.contains("approved for publication"))
    }

    @Test
    fun `publish succeeds when approval required and episode approved`() {
        every { targetService.get("pod1", "soundcloud") } returns enabledTarget
        every { publicationRepository.findByEpisodeIdAndTarget(1L, "soundcloud") } returns null
        every { publicationRepository.save(any()) } answers { firstArg<EpisodePublication>().copy(id = 10L) }
        val approvalPodcast = podcast.copy(requirePublishApproval = true)
        val approved = episode.copy(publishApproved = true)
        every { publisher.publish(approved, approvalPodcast, "user1") } returns PublishResult("sc-123", "https://soundcloud.com/track/123")

        val result = service.publish(approved, approvalPodcast, "user1", "soundcloud")

        assertEquals(PublicationStatus.PUBLISHED, result.status)
    }

    @Test
    fun `publish ignores publishApproved when approval not required`() {
        every { targetService.get("pod1", "soundcloud") } returns enabledTarget
        every { publicationRepository.findByEpisodeIdAndTarget(1L, "soundcloud") } returns null
        every { publicationRepository.save(any()) } answers { firstArg<EpisodePublication>().copy(id = 10L) }
        val unapproved = episode.copy(publishApproved = false)
        every { publisher.publish(unapproved, podcast, "user1") } returns PublishResult("sc-123", "https://soundcloud.com/track/123")

        val result = service.publish(unapproved, podcast, "user1", "soundcloud")

        assertEquals(PublicationStatus.PUBLISHED, result.status)
    }

    @Test
    fun `publish throws when episode has no audio`() {
        every { targetService.get("pod1", "soundcloud") } returns enabledTarget
        val noAudioEpisode = episode.copy(audioFilePath = null)

        assertThrows<IllegalStateException> {
            service.publish(noAudioEpisode, podcast, "user1", "soundcloud")
        }
    }

    @Test
    fun `publish throws when target not configured`() {
        every { targetService.get("pod1", "soundcloud") } returns null

        val ex = assertThrows<IllegalStateException> {
            service.publish(episode, podcast, "user1", "soundcloud")
        }
        assertTrue(ex.message!!.contains("not configured or enabled"))
    }

    @Test
    fun `publish throws when target is disabled`() {
        every { targetService.get("pod1", "soundcloud") } returns enabledTarget.copy(enabled = false)

        val ex = assertThrows<IllegalStateException> {
            service.publish(episode, podcast, "user1", "soundcloud")
        }
        assertTrue(ex.message!!.contains("not configured or enabled"))
    }

    @Test
    fun `publish updates existing publication when already published`() {
        every { targetService.get("pod1", "soundcloud") } returns enabledTarget
        val existing = EpisodePublication(
            id = 5L,
            episodeId = 1L,
            target = "soundcloud",
            status = PublicationStatus.PUBLISHED,
            externalId = "sc-123",
            externalUrl = "https://soundcloud.com/old",
            createdAt = "2026-02-13T10:00:00Z"
        )
        every { publicationRepository.findByEpisodeIdAndTarget(1L, "soundcloud") } returns existing
        every { publisher.update(episode, podcast, "user1", "sc-123") } returns PublishResult("sc-123", "https://soundcloud.com/updated")
        every { publicationRepository.save(any()) } answers { firstArg() }

        val result = service.publish(episode, podcast, "user1", "soundcloud")

        assertEquals(PublicationStatus.PUBLISHED, result.status)
        assertEquals("https://soundcloud.com/updated", result.externalUrl)
    }

    @Test
    fun `publish throws when update not supported`() {
        every { targetService.get("pod1", "soundcloud") } returns enabledTarget
        val existing = EpisodePublication(
            id = 5L,
            episodeId = 1L,
            target = "soundcloud",
            status = PublicationStatus.PUBLISHED,
            externalId = "sc-123",
            createdAt = "2026-02-13T10:00:00Z"
        )
        every { publicationRepository.findByEpisodeIdAndTarget(1L, "soundcloud") } returns existing
        every { publisher.update(episode, podcast, "user1", "sc-123") } throws UnsupportedOperationException("not supported")

        assertThrows<UnsupportedOperationException> {
            service.publish(episode, podcast, "user1", "soundcloud")
        }
    }

    @Test
    fun `publish records failure when publisher throws`() {
        every { targetService.get("pod1", "soundcloud") } returns enabledTarget
        every { publicationRepository.findByEpisodeIdAndTarget(1L, "soundcloud") } returns null
        every { publicationRepository.save(any()) } answers { firstArg<EpisodePublication>().copy(id = 10L) }
        every { publisher.publish(episode, podcast, "user1") } throws RuntimeException("API error")

        assertThrows<RuntimeException> {
            service.publish(episode, podcast, "user1", "soundcloud")
        }
    }

    // --- unpublish tests ---

    @Test
    fun `unpublish succeeds for published episode`() {
        val publication = EpisodePublication(
            id = 5L, episodeId = 1L, target = "soundcloud",
            status = PublicationStatus.PUBLISHED, externalId = "sc-123",
            createdAt = "2026-02-13T10:00:00Z"
        )
        every { publicationRepository.findByEpisodeIdAndTarget(1L, "soundcloud") } returns publication
        every { publisher.unpublish("user1", "sc-123") } returns Unit
        every { publicationRepository.save(any()) } answers { firstArg() }
        every { publicationRepository.findPublishedByPodcastIdAndTarget("pod1", "soundcloud") } returns emptyList()

        val result = service.unpublish(episode, podcast, "user1", "soundcloud")

        assertEquals(PublicationStatus.UNPUBLISHED, result.status)
        assertNull(result.externalId)
        verify { publisher.unpublish("user1", "sc-123") }
    }

    @Test
    fun `unpublish throws when not published`() {
        val publication = EpisodePublication(
            id = 5L, episodeId = 1L, target = "soundcloud",
            status = PublicationStatus.FAILED,
            createdAt = "2026-02-13T10:00:00Z"
        )
        every { publicationRepository.findByEpisodeIdAndTarget(1L, "soundcloud") } returns publication

        assertThrows<IllegalStateException> {
            service.unpublish(episode, podcast, "user1", "soundcloud")
        }
    }

    @Test
    fun `unpublish throws for unknown target`() {
        assertThrows<IllegalArgumentException> {
            service.unpublish(episode, podcast, "user1", "youtube")
        }
    }
}
