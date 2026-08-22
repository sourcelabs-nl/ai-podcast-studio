package com.aisummarypodcast.publishing

import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodePublication
import com.aisummarypodcast.store.EpisodePublicationRepository
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastPublicationTarget
import com.aisummarypodcast.store.PublicationStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
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
        coEvery { publisher.publish(episode, podcast, "user1") } returns PublishResult("sc-123", "https://soundcloud.com/track/123")

        val result = runBlocking { service.publish(episode, podcast, "user1", "soundcloud") }

        assertEquals(PublicationStatus.PUBLISHED, result.status)
        assertEquals("sc-123", result.externalId)
    }

    @Test
    fun `publish throws for unknown target`() {
        assertThrows<IllegalArgumentException> {
            runBlocking { service.publish(episode, podcast, "user1", "youtube") }
        }
    }

    @Test
    fun `publish throws when episode not generated`() {
        every { targetService.get("pod1", "soundcloud") } returns enabledTarget
        val pendingEpisode = episode.copy(status = EpisodeStatus.PENDING_REVIEW)

        assertThrows<IllegalStateException> {
            runBlocking { service.publish(pendingEpisode, podcast, "user1", "soundcloud") }
        }
    }

    @Test
    fun `publish throws when approval required and episode not approved`() {
        every { targetService.get("pod1", "soundcloud") } returns enabledTarget
        val approvalPodcast = podcast.copy(requirePublishApproval = true)
        val unapproved = episode.copy(publishApproved = false)

        val ex = assertThrows<IllegalStateException> {
            runBlocking { service.publish(unapproved, approvalPodcast, "user1", "soundcloud") }
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
        coEvery { publisher.publish(approved, approvalPodcast, "user1") } returns PublishResult("sc-123", "https://soundcloud.com/track/123")

        val result = runBlocking { service.publish(approved, approvalPodcast, "user1", "soundcloud") }

        assertEquals(PublicationStatus.PUBLISHED, result.status)
    }

    @Test
    fun `publish ignores publishApproved when approval not required`() {
        every { targetService.get("pod1", "soundcloud") } returns enabledTarget
        every { publicationRepository.findByEpisodeIdAndTarget(1L, "soundcloud") } returns null
        every { publicationRepository.save(any()) } answers { firstArg<EpisodePublication>().copy(id = 10L) }
        val unapproved = episode.copy(publishApproved = false)
        coEvery { publisher.publish(unapproved, podcast, "user1") } returns PublishResult("sc-123", "https://soundcloud.com/track/123")

        val result = runBlocking { service.publish(unapproved, podcast, "user1", "soundcloud") }

        assertEquals(PublicationStatus.PUBLISHED, result.status)
    }

    @Test
    fun `publish throws when episode has no audio`() {
        every { targetService.get("pod1", "soundcloud") } returns enabledTarget
        val noAudioEpisode = episode.copy(audioFilePath = null)

        assertThrows<IllegalStateException> {
            runBlocking { service.publish(noAudioEpisode, podcast, "user1", "soundcloud") }
        }
    }

    @Test
    fun `publish throws when target not configured`() {
        every { targetService.get("pod1", "soundcloud") } returns null

        val ex = assertThrows<IllegalStateException> {
            runBlocking { service.publish(episode, podcast, "user1", "soundcloud") }
        }
        assertTrue(ex.message!!.contains("not configured or enabled"))
    }

    @Test
    fun `publish throws when target is disabled`() {
        every { targetService.get("pod1", "soundcloud") } returns enabledTarget.copy(enabled = false)

        val ex = assertThrows<IllegalStateException> {
            runBlocking { service.publish(episode, podcast, "user1", "soundcloud") }
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
        coEvery { publisher.update(episode, podcast, "user1", "sc-123") } returns PublishResult("sc-123", "https://soundcloud.com/updated")
        every { publicationRepository.save(any()) } answers { firstArg() }

        val result = runBlocking { service.publish(episode, podcast, "user1", "soundcloud") }

        assertEquals(PublicationStatus.PUBLISHED, result.status)
        assertEquals("https://soundcloud.com/updated", result.externalUrl)
    }

    @Test
    fun `publish persists the new external id when an update changes it`() {
        // An update is not obliged to keep the same external identity: SoundCloud returns a new
        // track id because it cannot replace audio in place, and the FTP id follows the audio
        // filename. Keeping the old id left the row pointing at something that no longer exists.
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
        coEvery { publisher.update(episode, podcast, "user1", "sc-123") } returns
            PublishResult("sc-999", "https://soundcloud.com/replacement")
        every { publicationRepository.save(any()) } answers { firstArg() }

        val result = runBlocking { service.publish(episode, podcast, "user1", "soundcloud") }

        assertEquals("sc-999", result.externalId)
        assertEquals("https://soundcloud.com/replacement", result.externalUrl)
        verify { publicationRepository.save(match { it.externalId == "sc-999" }) }
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
        coEvery { publisher.update(episode, podcast, "user1", "sc-123") } throws UnsupportedOperationException("not supported")

        assertThrows<UnsupportedOperationException> {
            runBlocking { service.publish(episode, podcast, "user1", "soundcloud") }
        }
    }

    @Test
    fun `publish records failure when publisher throws`() {
        every { targetService.get("pod1", "soundcloud") } returns enabledTarget
        every { publicationRepository.findByEpisodeIdAndTarget(1L, "soundcloud") } returns null
        every { publicationRepository.save(any()) } answers { firstArg<EpisodePublication>().copy(id = 10L) }
        coEvery { publisher.publish(episode, podcast, "user1") } throws RuntimeException("API error")

        assertThrows<RuntimeException> {
            runBlocking { service.publish(episode, podcast, "user1", "soundcloud") }
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
