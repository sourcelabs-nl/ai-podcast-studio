package com.aisummarypodcast.publishing

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.BriefingProperties
import com.aisummarypodcast.config.EncryptionProperties
import com.aisummarypodcast.config.EpisodesProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.LlmProperties
import com.aisummarypodcast.podcast.EpisodeSourcesGenerator
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodePublication
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastPublicationTarget
import com.aisummarypodcast.store.PublicationStatus
import tools.jackson.databind.json.JsonMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.HttpClientErrorException
import org.springframework.http.HttpStatusCode

class SoundCloudPublisherTest {

    private val soundCloudClient = mockk<SoundCloudClient> {
        every { getMe(any()) } returns SoundCloudMeResponse(
            id = 1L,
            username = "testuser",
            plan = "Free",
            quota = SoundCloudQuota(unlimitedUploadQuota = false, uploadSecondsUsed = 1000, uploadSecondsLeft = 5000)
        )
    }
    private val tokenManager = mockk<SoundCloudTokenManager>()
    private val targetService = mockk<PodcastPublicationTargetService>(relaxed = true) {
        every { get("pod1", "soundcloud") } returns null
        every { upsert(any(), any(), any(), any(), any()) } answers {
            PodcastPublicationTarget(id = 1, podcastId = arg(0), target = arg(1), config = arg(2), enabled = arg(3), autoPublish = arg(4))
        }
    }
    private val objectMapper = JsonMapper()
    private val appProperties = AppProperties(
        llm = LlmProperties(),
        briefing = BriefingProperties(),
        episodes = EpisodesProperties(),
        feed = FeedProperties(baseUrl = "http://localhost:8085"),
        encryption = EncryptionProperties(masterKey = "test")
    )
    private val episodeSourcesGenerator = mockk<EpisodeSourcesGenerator> {
        every { deriveSlug(any()) } returns "test-slug"
    }
    private val publisher = SoundCloudPublisher(soundCloudClient, tokenManager, targetService, objectMapper, appProperties, episodeSourcesGenerator)

    private val expectedSourcesUrl = "http://localhost:8085/data/pod1/episodes/test-slug-sources.html"

    private val podcast = Podcast(id = "pod1", userId = "user1", name = "Tech News", topic = "AI, machine learning")
    private val episode = Episode(
        id = 1L,
        podcastId = "pod1",
        generatedAt = "2026-02-13T10:00:00Z",
        scriptText = "This is the episode script about AI and tech news.",
        status = EpisodeStatus.GENERATED,
        audioFilePath = "/tmp/test.mp3"
    )

    private val trackResponse = SoundCloudTrackResponse(id = 456, permalinkUrl = "https://soundcloud.com/user/tech-news")
    private val playlistResponse = SoundCloudPlaylistResponse(id = 789)

    @Test
    fun `targetName returns soundcloud`() {
        assertEquals("soundcloud", publisher.targetName())
    }

    @Test
    fun `publish uploads track with correct metadata`() = runTest {
        every { tokenManager.getValidAccessToken("user1") } returns "access-token"
        val requestSlot = slot<TrackUploadRequest>()
        every { soundCloudClient.uploadTrack("access-token", capture(requestSlot)) } returns trackResponse

        val result = publisher.publish(episode, podcast, "user1")

        assertEquals("456", result.externalId)
        assertEquals("https://soundcloud.com/user/tech-news", result.externalUrl)

        val uploadRequest = requestSlot.captured
        assertEquals("Tech News - 2026-02-13", uploadRequest.title)
        assertEquals("tech-news-2026-02-13", uploadRequest.permalink)
        assertEquals("This is the episode script about AI and tech news.\n\nFor the full list of sources and show notes: $expectedSourcesUrl", uploadRequest.description)
        assertEquals("AI \"machine learning\"", uploadRequest.tagList)
    }

    @Test
    fun `publish throws when no OAuth connection`() {
        every { tokenManager.getValidAccessToken("user1") } throws
            IllegalStateException("No SoundCloud connection found")

        assertThrows<IllegalStateException> {
            runBlocking { publisher.publish(episode, podcast, "user1") }
        }
    }

    @Test
    fun `publish truncates description to 500 chars`() {
        val longScript = "A".repeat(600)
        val longEpisode = episode.copy(scriptText = longScript)

        every { tokenManager.getValidAccessToken("user1") } returns "access-token"
        val requestSlot = slot<TrackUploadRequest>()
        every { soundCloudClient.uploadTrack("access-token", capture(requestSlot)) } returns trackResponse

        runBlocking { publisher.publish(longEpisode, podcast, "user1") }

        assertTrue(requestSlot.captured.description.startsWith("A".repeat(500)), "Expected script truncated to 500 chars")
        assertTrue(requestSlot.captured.description.contains(expectedSourcesUrl), "Expected sources URL in description")
    }

    @Test
    fun `publish uses show notes for description when available`() {
        val episodeWithNotes = episode.copy(
            showNotes = "Recap.\n\nSources:\n- Article\n  https://example.com/1"
        )

        every { tokenManager.getValidAccessToken("user1") } returns "access-token"
        val requestSlot = slot<TrackUploadRequest>()
        every { soundCloudClient.uploadTrack("access-token", capture(requestSlot)) } returns trackResponse

        runBlocking { publisher.publish(episodeWithNotes, podcast, "user1") }

        assertEquals("Recap.\n\nSources:\n- Article\n  https://example.com/1\n\nFor the full list of sources and show notes: $expectedSourcesUrl", requestSlot.captured.description)
    }

    @Test
    fun `publish uses recap when no show notes`() {
        val episodeWithRecap = episode.copy(recap = "A short recap")

        every { tokenManager.getValidAccessToken("user1") } returns "access-token"
        val requestSlot = slot<TrackUploadRequest>()
        every { soundCloudClient.uploadTrack("access-token", capture(requestSlot)) } returns trackResponse

        runBlocking { publisher.publish(episodeWithRecap, podcast, "user1") }

        assertEquals("A short recap\n\nFor the full list of sources and show notes: $expectedSourcesUrl", requestSlot.captured.description)
    }

    @Test
    fun `buildTagList handles multi-word tags`() {
        val podcastWithTags = podcast.copy(topic = "AI, machine learning, Kotlin")
        every { tokenManager.getValidAccessToken("user1") } returns "access-token"
        val requestSlot = slot<TrackUploadRequest>()
        every { soundCloudClient.uploadTrack("access-token", capture(requestSlot)) } returns trackResponse

        runBlocking { publisher.publish(episode, podcastWithTags, "user1") }

        assertEquals("AI \"machine learning\" Kotlin", requestSlot.captured.tagList)
    }

    @Test
    fun `rebuildPlaylist sets all track IDs on existing playlist`() {
        every { targetService.get("pod1", "soundcloud") } returns
            PodcastPublicationTarget(id = 1, podcastId = "pod1", target = "soundcloud", config = """{"playlistId":"123"}""", enabled = true)
        every { tokenManager.getValidAccessToken("user1") } returns "access-token"
        every { soundCloudClient.addTrackToPlaylist("access-token", 123, listOf(100, 200, 300)) } returns playlistResponse

        publisher.rebuildPlaylist(podcast, "user1", listOf(100, 200, 300))

        verify { soundCloudClient.addTrackToPlaylist("access-token", 123, listOf(100, 200, 300)) }
        verify(exactly = 0) { soundCloudClient.createPlaylist(any(), any(), any()) }
    }

    @Test
    fun `rebuildPlaylist creates new playlist when no playlist ID exists`() {
        every { tokenManager.getValidAccessToken("user1") } returns "access-token"
        every { soundCloudClient.createPlaylist("access-token", "Tech News", listOf(100, 200)) } returns playlistResponse

        publisher.rebuildPlaylist(podcast, "user1", listOf(100, 200))

        verify { soundCloudClient.createPlaylist("access-token", "Tech News", listOf(100, 200)) }
        verify { targetService.upsert("pod1", "soundcloud", match { it.contains("789") }, any(), any()) }
    }

    @Test
    fun `rebuildPlaylist creates new playlist when existing playlist returns 404`() {
        every { targetService.get("pod1", "soundcloud") } returns
            PodcastPublicationTarget(id = 1, podcastId = "pod1", target = "soundcloud", config = """{"playlistId":"999"}""", enabled = true)
        every { tokenManager.getValidAccessToken("user1") } returns "access-token"
        every { soundCloudClient.addTrackToPlaylist("access-token", 999, listOf(100)) } throws
            HttpClientErrorException.create(HttpStatusCode.valueOf(404), "Not Found", org.springframework.http.HttpHeaders(), ByteArray(0), null)
        every { soundCloudClient.createPlaylist("access-token", "Tech News", listOf(100)) } returns playlistResponse

        publisher.rebuildPlaylist(podcast, "user1", listOf(100))

        verify { targetService.upsert("pod1", "soundcloud", match { it.contains("789") }, true, false) }
    }

    @Test
    fun `updateTrackPermalinks updates each track with correct permalink`() {
        val episode1 = episode.copy(id = 1L, generatedAt = "2026-02-13T10:00:00Z")
        val episode2 = episode.copy(id = 2L, generatedAt = "2026-02-14T10:00:00Z")
        val pub1 = EpisodePublication(id = 10, episodeId = 1L, target = "soundcloud", status = PublicationStatus.PUBLISHED, externalId = "100", createdAt = "2026-02-13T10:00:00Z")
        val pub2 = EpisodePublication(id = 11, episodeId = 2L, target = "soundcloud", status = PublicationStatus.PUBLISHED, externalId = "200", createdAt = "2026-02-14T10:00:00Z")

        every { tokenManager.getValidAccessToken("user1") } returns "access-token"
        val expectedDescription = "${episode.scriptText.take(500)}\n\nFor the full list of sources and show notes: $expectedSourcesUrl"
        every { soundCloudClient.updateTrack("access-token", 100, "tech-news-2026-02-13", expectedDescription) } returns trackResponse
        every { soundCloudClient.updateTrack("access-token", 200, "tech-news-2026-02-14", expectedDescription) } returns trackResponse

        publisher.updateTrackPermalinks(podcast, "user1", listOf(episode1, episode2), listOf(pub1, pub2))

        verify { soundCloudClient.updateTrack("access-token", 100, "tech-news-2026-02-13", expectedDescription) }
        verify { soundCloudClient.updateTrack("access-token", 200, "tech-news-2026-02-14", expectedDescription) }
    }

    @Test
    fun `update calls updateTrack with show notes description`() = runTest {
        val episodeWithNotes = episode.copy(
            showNotes = "Recap.\n\nSources:\n- Article\n  https://example.com/1"
        )
        every { tokenManager.getValidAccessToken("user1") } returns "access-token"
        val expectedDescription = "Recap.\n\nSources:\n- Article\n  https://example.com/1\n\nFor the full list of sources and show notes: $expectedSourcesUrl"
        every { soundCloudClient.updateTrack("access-token", 456, description = expectedDescription) } returns trackResponse

        val result = publisher.update(episodeWithNotes, podcast, "user1", "456")

        assertEquals("456", result.externalId)
        assertEquals("https://soundcloud.com/user/tech-news", result.externalUrl)
        verify { soundCloudClient.updateTrack("access-token", 456, description = expectedDescription) }
    }

    @Test
    fun `update falls back to recap when no show notes`() = runTest {
        val episodeWithRecap = episode.copy(recap = "Short recap")
        every { tokenManager.getValidAccessToken("user1") } returns "access-token"
        every { soundCloudClient.updateTrack("access-token", 456, description = "Short recap\n\nFor the full list of sources and show notes: $expectedSourcesUrl") } returns trackResponse

        val result = publisher.update(episodeWithRecap, podcast, "user1", "456")

        assertEquals("456", result.externalId)
    }

    @Test
    fun `publish uploads anyway when quota exceeded but no deletable tracks exist`() {
        every { tokenManager.getValidAccessToken("user1") } returns "access-token"
        every { soundCloudClient.getMe("access-token") } returns SoundCloudMeResponse(
            id = 1L, username = "testuser", plan = "Free",
            quota = SoundCloudQuota(unlimitedUploadQuota = false, uploadSecondsUsed = 7000, uploadSecondsLeft = -500)
        )
        every { soundCloudClient.getMyTracks("access-token") } returns SoundCloudTrackListResponse()
        every { soundCloudClient.uploadTrack("access-token", any()) } returns trackResponse

        val result = runBlocking { publisher.publish(episode, podcast, "user1") }

        assertEquals("456", result.externalId)
        verify(exactly = 0) { soundCloudClient.deleteTrack(any(), any()) }
    }

    @Test
    fun `publish deletes oldest tracks until episode duration fits then uploads`() = runTest {
        // 5 minutes left, this episode needs 12 minutes -> must free ~7 min (+ buffer).
        // runTest skips the publisher's post-deletion delay() via virtual time, so this stays fast.
        every { tokenManager.getValidAccessToken("user1") } returns "access-token"
        every { soundCloudClient.getMe("access-token") } returns SoundCloudMeResponse(
            id = 1L, username = "testuser", plan = "Free",
            quota = SoundCloudQuota(unlimitedUploadQuota = false, uploadSecondsUsed = 7000, uploadSecondsLeft = 300)
        )
        every { soundCloudClient.getMyTracks("access-token") } returns SoundCloudTrackListResponse(
            collection = listOf(
                // Durations in milliseconds; oldest first by createdAt.
                SoundCloudTrackSummary(id = 100, title = "Tech News - 2026-01-01", createdAt = "2026-01-01T00:00:00Z", duration = 360_000),
                SoundCloudTrackSummary(id = 200, title = "Tech News - 2026-01-02", createdAt = "2026-01-02T00:00:00Z", duration = 360_000),
                SoundCloudTrackSummary(id = 300, title = "Tech News - 2026-01-03", createdAt = "2026-01-03T00:00:00Z", duration = 360_000),
                SoundCloudTrackSummary(id = 999, title = "Other Show - 2026-01-01", createdAt = "2025-01-01T00:00:00Z", duration = 360_000)
            )
        )
        every { soundCloudClient.deleteTrack("access-token", any()) } returns Unit
        every { soundCloudClient.uploadTrack("access-token", any()) } returns trackResponse

        val result = publisher.publish(episode.copy(durationSeconds = 720), podcast, "user1")

        assertEquals("456", result.externalId)
        // Need 720 - 300 + 120 = 540s; each track is 360s, so the two oldest podcast tracks (100, 200)
        // are deleted while the newer (300) and other-podcast (999) tracks are kept.
        verify { soundCloudClient.deleteTrack("access-token", 100) }
        verify { soundCloudClient.deleteTrack("access-token", 200) }
        verify(exactly = 0) { soundCloudClient.deleteTrack("access-token", 300) }
        verify(exactly = 0) { soundCloudClient.deleteTrack("access-token", 999) }
    }

    @Test
    fun `publish proceeds when quota fits the episode duration`() {
        every { tokenManager.getValidAccessToken("user1") } returns "access-token"
        every { soundCloudClient.getMe("access-token") } returns SoundCloudMeResponse(
            id = 1L, username = "testuser", plan = "Free",
            quota = SoundCloudQuota(unlimitedUploadQuota = false, uploadSecondsUsed = 7000, uploadSecondsLeft = 800)
        )
        every { soundCloudClient.uploadTrack("access-token", any()) } returns trackResponse

        val result = runBlocking { publisher.publish(episode.copy(durationSeconds = 720), podcast, "user1") }
        assertEquals("456", result.externalId)
    }

    @Test
    fun `publish skips quota check when unlimited`() {
        every { tokenManager.getValidAccessToken("user1") } returns "access-token"
        every { soundCloudClient.getMe("access-token") } returns SoundCloudMeResponse(
            id = 1L, username = "testuser", plan = "Pro",
            quota = SoundCloudQuota(unlimitedUploadQuota = true, uploadSecondsUsed = 99999, uploadSecondsLeft = -500)
        )
        every { soundCloudClient.uploadTrack("access-token", any()) } returns trackResponse

        val result = runBlocking { publisher.publish(episode, podcast, "user1") }
        assertEquals("456", result.externalId)
    }

    @Test
    fun `description includes owner email when configured`() {
        val publisherWithEmail = SoundCloudPublisher(
            soundCloudClient, tokenManager, targetService, objectMapper,
            appProperties.copy(feed = appProperties.feed.copy(ownerEmail = "host@example.com")),
            episodeSourcesGenerator
        )
        every { tokenManager.getValidAccessToken("user1") } returns "access-token"
        val requestSlot = slot<TrackUploadRequest>()
        every { soundCloudClient.uploadTrack("access-token", capture(requestSlot)) } returns trackResponse

        runBlocking { publisherWithEmail.publish(episode, podcast, "user1") }

        assertTrue(requestSlot.captured.description.contains("Tips, comments, or feedback? Mail us at host@example.com"))
    }

}
