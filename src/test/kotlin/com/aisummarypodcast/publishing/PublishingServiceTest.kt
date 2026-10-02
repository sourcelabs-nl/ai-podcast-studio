package com.aisummarypodcast.publishing

import com.aisummarypodcast.api.InvalidRequestException
import com.aisummarypodcast.api.OperationFailedException
import com.aisummarypodcast.podcast.PodcastEvent
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
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
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
    fun `listByPodcast pages in the database and carries the episode reference and the total`() {
        val pub = EpisodePublication(id = 7L, episodeId = 1L, target = "rss", status = PublicationStatus.PUBLISHED, createdAt = "now")
        every { publicationRepository.findPageByPodcastId("pod1", 20, 40) } returns listOf(pub)
        every { publicationRepository.countByPodcastId("pod1") } returns 41L
        every { episodeRepository.findAllById(listOf(1L)) } returns listOf(episode)

        val page = service.listByPodcast("pod1", org.springframework.data.domain.PageRequest.of(2, 20))

        assertEquals(41L, page.totalElements)
        val row = page.content.single()
        assertEquals(7L, row.publication.id)
        assertEquals(1L, row.episode.id)
        assertEquals("2026-02-13T10:00:00Z", row.episode.generatedAt)
        assertEquals("GENERATED", row.episode.status)
    }

    @Test
    fun `liveTargets returns only targets with a published publication`() {
        every { publicationRepository.findByEpisodeId(1L) } returns listOf(
            EpisodePublication(id = 1L, episodeId = 1L, target = "soundcloud", status = PublicationStatus.PUBLISHED, createdAt = "now"),
            EpisodePublication(id = 2L, episodeId = 1L, target = "ftp", status = PublicationStatus.UNPUBLISHED, createdAt = "now")
        )

        assertEquals(listOf("soundcloud"), service.liveTargets(1L))
    }

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
    fun `publishing a focus episode keeps the same-day regular publication`() {
        val focusEpisode = episode.copy(id = 2L, focus = "Claude Opus 5.5 release")
        val regularPublication = EpisodePublication(id = 5L, episodeId = 1L, target = "soundcloud", status = PublicationStatus.PUBLISHED, externalId = "sc-1", createdAt = "now")
        every { targetService.get("pod1", "soundcloud") } returns enabledTarget
        every { publicationRepository.findByEpisodeIdAndTarget(2L, "soundcloud") } returns null
        every { publicationRepository.findPublishedByPodcastIdAndTarget("pod1", "soundcloud") } returns listOf(regularPublication)
        every { episodeRepository.findById(1L) } returns java.util.Optional.of(episode)
        every { publicationRepository.save(any()) } answers { firstArg<EpisodePublication>().copy(id = 10L) }
        coEvery { publisher.publish(focusEpisode, podcast, "user1") } returns PublishResult("sc-2", "https://soundcloud.com/track/2")

        runBlocking { service.publish(focusEpisode, podcast, "user1", "soundcloud") }

        verify(exactly = 0) { publicationRepository.delete(regularPublication) }
        verify(exactly = 0) { publisher.unpublish(any(), "sc-1") }
    }

    @Test
    fun `publishing a regenerated regular episode replaces the same-day regular publication`() {
        val regenerated = episode.copy(id = 2L)
        val previous = EpisodePublication(id = 5L, episodeId = 1L, target = "soundcloud", status = PublicationStatus.PUBLISHED, externalId = "sc-1", createdAt = "now")
        every { targetService.get("pod1", "soundcloud") } returns enabledTarget
        every { publicationRepository.findByEpisodeIdAndTarget(2L, "soundcloud") } returns null
        every { publicationRepository.findPublishedByPodcastIdAndTarget("pod1", "soundcloud") } returns listOf(previous)
        every { episodeRepository.findById(1L) } returns java.util.Optional.of(episode)
        every { publisher.unpublish("user1", "sc-1") } returns Unit
        every { publicationRepository.save(any()) } answers { firstArg<EpisodePublication>().copy(id = 10L) }
        coEvery { publisher.publish(regenerated, podcast, "user1") } returns PublishResult("sc-2", "https://soundcloud.com/track/2")

        runBlocking { service.publish(regenerated, podcast, "user1", "soundcloud") }

        verify { publicationRepository.delete(previous) }
    }

    @Test
    fun `a failed same-day unpublish keeps the previous record and still publishes the new episode`() {
        val regenerated = episode.copy(id = 2L)
        val previous = EpisodePublication(id = 5L, episodeId = 1L, target = "soundcloud", status = PublicationStatus.PUBLISHED, externalId = "sc-1", createdAt = "now")
        every { targetService.get("pod1", "soundcloud") } returns enabledTarget
        every { publicationRepository.findByEpisodeIdAndTarget(2L, "soundcloud") } returns null
        every { publicationRepository.findPublishedByPodcastIdAndTarget("pod1", "soundcloud") } returns listOf(previous)
        every { episodeRepository.findById(1L) } returns java.util.Optional.of(episode)
        every { publisher.unpublish("user1", "sc-1") } throws RuntimeException("503 from SoundCloud")
        every { publicationRepository.save(any()) } answers { firstArg<EpisodePublication>().copy(id = firstArg<EpisodePublication>().id ?: 10L) }
        coEvery { publisher.publish(regenerated, podcast, "user1") } returns PublishResult("sc-2", "https://soundcloud.com/track/2")

        val result = runBlocking { service.publish(regenerated, podcast, "user1", "soundcloud") }

        assertEquals("sc-2", result.externalId)
        verify(exactly = 0) { publicationRepository.delete(any()) }
        verify {
            publicationRepository.save(match {
                it.id == 5L && it.externalId == "sc-1" && it.status == PublicationStatus.PUBLISHED &&
                    it.errorMessage == "503 from SoundCloud"
            })
        }
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

    @Test
    fun `publish keeps the publication PUBLISHED when a post-publish step fails`() {
        // The upload already landed, so a broken side effect must not overwrite the row: doing so
        // dropped episode 202's SoundCloud track id and left the track behind, blocking the
        // same-day replacement of its successor over a taken permalink.
        every { targetService.get("pod1", "soundcloud") } returns enabledTarget
        every { publicationRepository.findByEpisodeIdAndTarget(1L, "soundcloud") } returns null
        every { publicationRepository.save(any()) } answers { firstArg<EpisodePublication>().copy(id = 10L) }
        coEvery { publisher.publish(episode, podcast, "user1") } returns PublishResult("sc-123", "https://soundcloud.com/track/123")
        every { publisher.postPublish(any(), any()) } throws RuntimeException("hook exploded")

        val result = runBlocking { service.publish(episode, podcast, "user1", "soundcloud") }

        assertEquals(PublicationStatus.PUBLISHED, result.status)
        assertEquals("sc-123", result.externalId)
        verify(exactly = 0) { publicationRepository.save(match { it.status == PublicationStatus.FAILED }) }
        verify { eventPublisher.publishEvent(match<PodcastEvent> { it.event == "episode.published" }) }
        verify(exactly = 0) { eventPublisher.publishEvent(match<PodcastEvent> { it.event == "episode.publish.failed" }) }
    }

    @Test
    fun `publish keeps the publication PUBLISHED when the feed export fails`() {
        every { targetService.get("pod1", "soundcloud") } returns enabledTarget
        every { publicationRepository.findByEpisodeIdAndTarget(1L, "soundcloud") } returns null
        every { publicationRepository.save(any()) } answers { firstArg<EpisodePublication>().copy(id = 10L) }
        coEvery { publisher.publish(episode, podcast, "user1") } returns PublishResult("sc-123", "https://soundcloud.com/track/123")
        every { staticFeedExporter.export(podcast) } throws RuntimeException("disk full")

        val result = runBlocking { service.publish(episode, podcast, "user1", "soundcloud") }

        assertEquals(PublicationStatus.PUBLISHED, result.status)
        verify(exactly = 0) { publicationRepository.save(match { it.status == PublicationStatus.FAILED }) }
    }

    @Test
    fun `publish leaves the publication PENDING when the upload is cancelled`() {
        // A cancelled call proves nothing about whether the upload landed. Recording FAILED with a
        // null external id would assert something we do not know.
        every { targetService.get("pod1", "soundcloud") } returns enabledTarget
        every { publicationRepository.findByEpisodeIdAndTarget(1L, "soundcloud") } returns null
        every { publicationRepository.save(any()) } answers { firstArg<EpisodePublication>().copy(id = 10L) }
        coEvery { publisher.publish(episode, podcast, "user1") } throws CancellationException("MonoCoroutine was cancelled")

        assertThrows<CancellationException> {
            runBlocking { service.publish(episode, podcast, "user1", "soundcloud") }
        }

        verify(exactly = 0) { publicationRepository.save(match { it.status == PublicationStatus.FAILED }) }
        verify { publicationRepository.save(match { it.status == PublicationStatus.PENDING }) }
    }

    @Test
    fun `an update keeps its new external id when a post-publish step fails`() {
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
        every { publisher.postPublish(any(), any()) } throws RuntimeException("hook exploded")

        val result = runBlocking { service.publish(episode, podcast, "user1", "soundcloud") }

        assertEquals("sc-999", result.externalId)
        verify(exactly = 0) { publicationRepository.save(match { it.errorMessage != null }) }
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
    fun `unpublish keeps the publication PUBLISHED with its external id when the removal fails`() {
        val publication = EpisodePublication(
            id = 5L, episodeId = 1L, target = "soundcloud",
            status = PublicationStatus.PUBLISHED, externalId = "sc-123",
            createdAt = "2026-02-13T10:00:00Z"
        )
        every { publicationRepository.findByEpisodeIdAndTarget(1L, "soundcloud") } returns publication
        every { publisher.unpublish("user1", "sc-123") } throws RuntimeException("503 from SoundCloud")
        every { publicationRepository.save(any()) } answers { firstArg() }

        assertThrows<RuntimeException> { service.unpublish(episode, podcast, "user1", "soundcloud") }

        verify(exactly = 1) { publicationRepository.save(any()) }
        verify {
            publicationRepository.save(match {
                it.status == PublicationStatus.PUBLISHED && it.externalId == "sc-123" &&
                    it.errorMessage == "503 from SoundCloud"
            })
        }
        verify(exactly = 0) { eventPublisher.publishEvent(any<PodcastEvent>()) }
    }

    @Test
    fun `unpublish still succeeds when a step after the removal fails`() {
        val publication = EpisodePublication(
            id = 5L, episodeId = 1L, target = "soundcloud",
            status = PublicationStatus.PUBLISHED, externalId = "sc-123",
            createdAt = "2026-02-13T10:00:00Z"
        )
        every { publicationRepository.findByEpisodeIdAndTarget(1L, "soundcloud") } returns publication
        every { publisher.unpublish("user1", "sc-123") } returns Unit
        every { publicationRepository.save(any()) } answers { firstArg() }
        every { publicationRepository.findPublishedByPodcastIdAndTarget("pod1", "soundcloud") } returns emptyList()
        every { publisher.postPublish(podcast, "user1") } throws RuntimeException("hook down")
        every { staticFeedExporter.export(podcast) } throws RuntimeException("disk full")

        val result = service.unpublish(episode, podcast, "user1", "soundcloud")

        assertEquals(PublicationStatus.UNPUBLISHED, result.status)
        assertNull(result.externalId)
        verify { eventPublisher.publishEvent(match<PodcastEvent> { it.event == "episode.unpublished" }) }
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

    @Test
    fun `a rebuild marks publications whose SoundCloud track is gone`() {
        val live = EpisodePublication(
            id = 10L, episodeId = 1L, target = "soundcloud", externalId = "111",
            status = PublicationStatus.PUBLISHED, createdAt = "2026-02-13T10:00:00Z"
        )
        val gone = EpisodePublication(
            id = 11L, episodeId = 2L, target = "soundcloud", externalId = "222",
            status = PublicationStatus.PUBLISHED, createdAt = "2026-02-12T10:00:00Z"
        )
        val olderEpisode = episode.copy(id = 2L, generatedAt = "2026-02-12T10:00:00Z")
        every { publicationRepository.findPublishedByPodcastIdAndTarget("pod1", "soundcloud") } returns listOf(live, gone)
        every { episodeRepository.findByIdOrNull(1L) } returns episode
        every { episodeRepository.findByIdOrNull(2L) } returns olderEpisode
        every { soundCloudPublisher.updateTrackPermalinks(any(), any(), any(), any()) } returns setOf(222L)
        every { soundCloudPublisher.rebuildPlaylist(any(), any(), any()) } returns Unit
        val saved = mutableListOf<EpisodePublication>()
        every { publicationRepository.save(capture(saved)) } answers { firstArg() }

        service.rebuildSoundCloudPlaylist(podcast, "user1")

        // Only the dead track's row is corrected; the live one is left alone.
        assertEquals(1, saved.size)
        assertEquals(11L, saved.single().id)
        assertEquals(PublicationStatus.UNPUBLISHED, saved.single().status)
        assertNull(saved.single().externalId)
        verify { soundCloudPublisher.rebuildPlaylist(podcast, "user1", listOf(111L)) }
    }

    @Test
    fun `a rebuild with no stale tracks writes nothing`() {
        val live = EpisodePublication(
            id = 10L, episodeId = 1L, target = "soundcloud", externalId = "111",
            status = PublicationStatus.PUBLISHED, createdAt = "2026-02-13T10:00:00Z"
        )
        every { publicationRepository.findPublishedByPodcastIdAndTarget("pod1", "soundcloud") } returns listOf(live)
        every { episodeRepository.findByIdOrNull(1L) } returns episode
        every { soundCloudPublisher.updateTrackPermalinks(any(), any(), any(), any()) } returns emptySet()
        every { soundCloudPublisher.rebuildPlaylist(any(), any(), any()) } returns Unit

        service.rebuildSoundCloudPlaylist(podcast, "user1")

        verify(exactly = 0) { publicationRepository.save(any()) }
    }

    @Test
    fun `rebuild rejects a podcast without published SoundCloud tracks`() {
        every { publicationRepository.findPublishedByPodcastIdAndTarget("pod1", "soundcloud") } returns emptyList()

        val ex = assertThrows<InvalidRequestException> { service.rebuildSoundCloudPlaylist(podcast, "user1") }

        assertEquals("No published SoundCloud tracks found for this podcast", ex.message)
    }

    @Test
    fun `rebuild rejects publications without a valid track id`() {
        val noId = EpisodePublication(id = 1L, episodeId = 1L, target = "soundcloud", status = PublicationStatus.PUBLISHED, externalId = "abc", createdAt = "now")
        every { publicationRepository.findPublishedByPodcastIdAndTarget("pod1", "soundcloud") } returns listOf(noId)

        val ex = assertThrows<InvalidRequestException> { service.rebuildSoundCloudPlaylist(podcast, "user1") }

        assertEquals("No valid SoundCloud track IDs found", ex.message)
    }

    @Test
    fun `rebuild rejects a podcast whose tracks are all stale`() {
        val pub = EpisodePublication(id = 1L, episodeId = 1L, target = "soundcloud", status = PublicationStatus.PUBLISHED, externalId = "11", createdAt = "now")
        every { publicationRepository.findPublishedByPodcastIdAndTarget("pod1", "soundcloud") } returns listOf(pub)
        every { episodeRepository.findById(1L) } returns java.util.Optional.of(episode)
        every { soundCloudPublisher.updateTrackPermalinks(podcast, "user1", listOf(episode), listOf(pub)) } returns setOf(11L)

        val ex = assertThrows<InvalidRequestException> { service.rebuildSoundCloudPlaylist(podcast, "user1") }

        assertEquals("No active SoundCloud tracks found after filtering stale tracks", ex.message)
    }

    @Test
    fun `rebuild wraps a SoundCloud failure in an operation failure`() {
        val pub = EpisodePublication(id = 1L, episodeId = 1L, target = "soundcloud", status = PublicationStatus.PUBLISHED, externalId = "11", createdAt = "now")
        every { publicationRepository.findPublishedByPodcastIdAndTarget("pod1", "soundcloud") } returns listOf(pub)
        every { episodeRepository.findById(1L) } returns java.util.Optional.of(episode)
        every { soundCloudPublisher.updateTrackPermalinks(any(), any(), any(), any()) } returns emptySet()
        every { soundCloudPublisher.rebuildPlaylist(podcast, "user1", listOf(11L)) } throws RuntimeException("SoundCloud down")

        val ex = assertThrows<OperationFailedException> { service.rebuildSoundCloudPlaylist(podcast, "user1") }

        assertEquals("Failed to rebuild playlist: SoundCloud down", ex.message)
    }

    @Test
    fun `rebuild returns the track ids on success`() {
        val pub = EpisodePublication(id = 1L, episodeId = 1L, target = "soundcloud", status = PublicationStatus.PUBLISHED, externalId = "11", createdAt = "now")
        every { publicationRepository.findPublishedByPodcastIdAndTarget("pod1", "soundcloud") } returns listOf(pub)
        every { episodeRepository.findById(1L) } returns java.util.Optional.of(episode)
        every { soundCloudPublisher.updateTrackPermalinks(any(), any(), any(), any()) } returns emptySet()
        every { soundCloudPublisher.rebuildPlaylist(podcast, "user1", listOf(11L)) } returns Unit

        assertEquals(listOf(11L), service.rebuildSoundCloudPlaylist(podcast, "user1"))
    }
}
