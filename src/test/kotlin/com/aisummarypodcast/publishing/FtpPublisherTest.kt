package com.aisummarypodcast.publishing

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.podcast.EpisodeService
import com.aisummarypodcast.podcast.EpisodeSourcesGenerator
import com.aisummarypodcast.podcast.FeedGenerator
import com.aisummarypodcast.podcast.PodcastImageService
import com.aisummarypodcast.store.*
import com.aisummarypodcast.user.ProviderConfig
import com.aisummarypodcast.user.UserProviderConfigService
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.json.JsonMapper

class FtpPublisherTest {

    private val providerConfigService = mockk<UserProviderConfigService>()
    private val targetService = mockk<PodcastPublicationTargetService>()
    private val podcastImageService = mockk<PodcastImageService>()
    private val feedGenerator = mockk<FeedGenerator>()
    private val episodeSourcesGenerator = mockk<EpisodeSourcesGenerator>()
    private val episodeService = mockk<EpisodeService>()
    private val podcastRepository = mockk<PodcastRepository>()
    private val userRepository = mockk<UserRepository>()
    private val objectMapper = JsonMapper.builder().build()
    private val appProperties = mockk<AppProperties>().also {
        every { it.feed } returns FeedProperties(baseUrl = "http://localhost:8085")
    }

    private val publisher = FtpPublisher(
        providerConfigService, targetService, podcastImageService,
        feedGenerator, episodeSourcesGenerator, episodeService,
        podcastRepository, userRepository, objectMapper, appProperties
    )

    @Test
    fun `targetName returns ftp`() {
        assertEquals("ftp", publisher.targetName())
    }

    @Test
    fun `throws when no FTP credentials configured`() {
        every { providerConfigService.resolveConfig("user1", ApiKeyCategory.PUBLISHING, "ftp") } returns null

        val episode = Episode(id = 1, podcastId = "p1", generatedAt = "2026-01-01T00:00:00Z", scriptText = "test")
        val podcast = Podcast(id = "p1", userId = "user1", name = "Tech", topic = "tech")

        val ex = assertThrows<IllegalStateException> {
            runBlocking { publisher.publish(episode, podcast, "user1") }
        }
        assertTrue(ex.message!!.contains("No FTP credentials"))
    }

    @Test
    fun `throws when FTP target not configured for podcast`() {
        val credJson = """{"host":"ftp.example.com","port":21,"username":"user","password":"pass","useTls":true}"""
        every { providerConfigService.resolveConfig("user1", ApiKeyCategory.PUBLISHING, "ftp") } returns
            ProviderConfig(baseUrl = "", apiKey = credJson)
        every { targetService.get("p1", "ftp") } returns null

        val episode = Episode(id = 1, podcastId = "p1", generatedAt = "2026-01-01T00:00:00Z", scriptText = "test")
        val podcast = Podcast(id = "p1", userId = "user1", name = "Tech", topic = "tech")

        val ex = assertThrows<IllegalStateException> {
            runBlocking { publisher.publish(episode, podcast, "user1") }
        }
        assertTrue(ex.message!!.contains("not configured"))
    }

    @Test
    fun `throws when FTP target is disabled`() {
        val credJson = """{"host":"ftp.example.com","port":21,"username":"user","password":"pass","useTls":true}"""
        every { providerConfigService.resolveConfig("user1", ApiKeyCategory.PUBLISHING, "ftp") } returns
            ProviderConfig(baseUrl = "", apiKey = credJson)
        every { targetService.get("p1", "ftp") } returns PodcastPublicationTarget(
            id = 1, podcastId = "p1", target = "ftp",
            config = """{"remotePath":"/shows/"}""", enabled = false
        )

        val episode = Episode(id = 1, podcastId = "p1", generatedAt = "2026-01-01T00:00:00Z", scriptText = "test")
        val podcast = Podcast(id = "p1", userId = "user1", name = "Tech", topic = "tech")

        val ex = assertThrows<IllegalStateException> {
            runBlocking { publisher.publish(episode, podcast, "user1") }
        }
        assertTrue(ex.message!!.contains("disabled"))
    }
}
