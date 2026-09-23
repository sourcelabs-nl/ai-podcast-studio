package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.EpisodesProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.publishing.PodcastPublicationTargetService
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodePublication
import com.aisummarypodcast.store.EpisodePublicationRepository
import com.aisummarypodcast.store.EpisodeArticleRepository
import com.aisummarypodcast.store.EpisodeRepository
import com.aisummarypodcast.store.EpisodePurpose
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastPublicationTarget
import com.aisummarypodcast.store.PublicationStatus
import com.aisummarypodcast.store.User
import com.aisummarypodcast.store.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional

class StaticFeedExporterTest {

    private val episodeRepository = mockk<EpisodeRepository>()
    private val userRepository = mockk<UserRepository>()
    private val podcastImageService = mockk<PodcastImageService>()
    private val episodeSourcesGenerator = mockk<EpisodeSourcesGenerator>()
    private val publicationRepository = mockk<EpisodePublicationRepository>()
    private val targetService = mockk<PodcastPublicationTargetService>()
    private val episodeArticleRepository = mockk<EpisodeArticleRepository>().also {
        every { it.findArticlesByEpisodeIds(any()) } returns emptyMap()
    }
    private val objectMapper = JsonMapper.builder().build()

    @TempDir
    lateinit var tempDir: Path

    private fun createExporter(staticBaseUrl: String? = null): StaticFeedExporter {
        val feedProperties = FeedProperties(
            baseUrl = "http://localhost:8085",
            title = "AI Summary Podcast",
            description = "Test feed",
            staticBaseUrl = staticBaseUrl
        )
        val appProperties = mockk<AppProperties>().also {
            every { it.feed } returns feedProperties
            every { it.episodes } returns EpisodesProperties(directory = tempDir.toString())
        }
        val feedGenerator = FeedGenerator(episodeRepository, appProperties, podcastImageService, episodeSourcesGenerator, publicationRepository, episodeArticleRepository)
        return StaticFeedExporter(feedGenerator, userRepository, appProperties, targetService, objectMapper)
    }

    @Test
    fun `exports feed xml with podcast name as title`() {
        val podcast = Podcast(id = "p1", userId = "u1", name = "Tech", topic = "tech")
        val user = User(id = "u1", name = "Test User")
        every { userRepository.findById("u1") } returns Optional.of(user)
        every { episodeRepository.findByPodcastIdAndStatusAndPurposeNotOrderByGeneratedAtDescIdDesc("p1", EpisodeStatus.GENERATED, EpisodePurpose.EXPERIMENT) } returns emptyList()
        every { podcastImageService.get("p1") } returns null
        every { targetService.get("p1", "ftp") } returns null

        val exporter = createExporter()
        exporter.export(podcast)

        val feedFile = tempDir.resolve("p1/feed.xml")
        assertTrue(Files.exists(feedFile), "feed.xml should be created")
        val content = Files.readString(feedFile)
        assertTrue(content.contains("<rss"), "Should contain RSS XML")
        assertTrue(content.contains("<title>Tech</title>"), "Should contain podcast name as title")
    }

    @Test
    fun `uses static base url in enclosure urls when configured`() {
        val podcast = Podcast(id = "p1", userId = "u1", name = "Tech", topic = "tech")
        val user = User(id = "u1", name = "Test User")
        val episode = Episode(
            id = 1L, podcastId = "p1", generatedAt = "2025-01-01T00:00:00Z",
            scriptText = "Episode script", status = EpisodeStatus.GENERATED,
            audioFilePath = "/data/p1/episodes/briefing-20250101-000000.mp3", durationSeconds = 120
        )
        every { userRepository.findById("u1") } returns Optional.of(user)
        every { episodeRepository.findByPodcastIdAndStatusAndPurposeNotOrderByGeneratedAtDescIdDesc("p1", EpisodeStatus.GENERATED, EpisodePurpose.EXPERIMENT) } returns listOf(episode)
        every { podcastImageService.get("p1") } returns null
        every { targetService.get("p1", "ftp") } returns null
        every { episodeSourcesGenerator.deriveSlug(episode) } returns "briefing-20250101-000000"
        every { episodeArticleRepository.findArticlesByEpisodeIds(listOf(1L)) } returns emptyMap()

        val exporter = createExporter(staticBaseUrl = "https://cdn.example.com")
        exporter.export(podcast)

        val content = Files.readString(tempDir.resolve("p1/feed.xml"))
        assertTrue(
            content.contains("https://cdn.example.com/data/p1/episodes/briefing-20250101-000000.mp3"),
            "Enclosure URL should use static base URL"
        )
    }

    @Test
    fun `uses FTP publicUrl when available`() {
        val podcast = Podcast(id = "p1", userId = "u1", name = "Tech", topic = "tech")
        val user = User(id = "u1", name = "Test User")
        val episode = Episode(
            id = 1L, podcastId = "p1", generatedAt = "2025-01-01T00:00:00Z",
            scriptText = "Episode script", status = EpisodeStatus.GENERATED,
            audioFilePath = "/data/p1/episodes/briefing-20250101-000000.mp3", durationSeconds = 120,
            showNotes = "Today's recap."
        )
        val publication = EpisodePublication(
            id = 1L, episodeId = 1L, target = "ftp",
            status = PublicationStatus.PUBLISHED, externalId = "ftp:briefing-20250101-000000",
            createdAt = "2025-01-01T00:00:00Z"
        )
        every { userRepository.findById("u1") } returns Optional.of(user)
        every { episodeRepository.findByPodcastIdAndStatusAndPurposeNotOrderByGeneratedAtDescIdDesc("p1", EpisodeStatus.GENERATED, EpisodePurpose.EXPERIMENT) } returns listOf(episode)
        every { publicationRepository.findPublishedByPodcastIdAndTarget("p1", "ftp") } returns listOf(publication)
        every { podcastImageService.get("p1") } returns null
        every { episodeSourcesGenerator.deriveSlug(episode) } returns "briefing-20250101-000000"
        every { episodeArticleRepository.findArticlesByEpisodeIds(listOf(1L)) } returns emptyMap()
        every { targetService.get("p1", "ftp") } returns PodcastPublicationTarget(
            id = 1, podcastId = "p1", target = "ftp",
            config = """{"remotePath":"/shows/tech/","publicUrl":"https://podcast.example.com"}""",
            enabled = true
        )

        val exporter = createExporter()
        exporter.export(podcast)

        val content = Files.readString(tempDir.resolve("p1/feed.xml"))
        assertTrue(content.contains("Today's recap."), "Expected show notes in description")
        assertTrue(content.contains("https://podcast.example.com/shows/tech/episodes/briefing-20250101-000000-sources.html"), "Expected sources URL in content:encoded")
    }

    @Test
    fun `does not use disabled FTP target publicUrl`() {
        val podcast = Podcast(id = "p1", userId = "u1", name = "Tech", topic = "tech")
        val user = User(id = "u1", name = "Test User")
        every { userRepository.findById("u1") } returns Optional.of(user)
        every { episodeRepository.findByPodcastIdAndStatusAndPurposeNotOrderByGeneratedAtDescIdDesc("p1", EpisodeStatus.GENERATED, EpisodePurpose.EXPERIMENT) } returns emptyList()
        every { podcastImageService.get("p1") } returns null
        every { targetService.get("p1", "ftp") } returns PodcastPublicationTarget(
            id = 1, podcastId = "p1", target = "ftp",
            config = """{"publicUrl":"https://podcast.example.com/shows/tech/"}""",
            enabled = false
        )

        val exporter = createExporter()
        exporter.export(podcast)

        val content = Files.readString(tempDir.resolve("p1/feed.xml"))
        assertFalse(content.contains("podcast.example.com"), "Should not use disabled target's publicUrl")
    }

    @Test
    fun `logs warning on write failure without throwing`() {
        val podcast = Podcast(id = "p1", userId = "u1", name = "Tech", topic = "tech")
        val user = User(id = "u1", name = "Test User")
        every { userRepository.findById("u1") } returns Optional.of(user)
        every { targetService.get("p1", "ftp") } returns null

        val feedGenerator = mockk<FeedGenerator>()
        every { feedGenerator.generate(any(), any(), any(), any()) } throws RuntimeException("disk full")

        val appProperties = mockk<AppProperties>().also {
            every { it.feed } returns FeedProperties(staticBaseUrl = null)
            every { it.episodes } returns EpisodesProperties(directory = tempDir.toString())
        }

        val exporter = StaticFeedExporter(feedGenerator, userRepository, appProperties, targetService, objectMapper)
        exporter.export(podcast)

        verify { feedGenerator.generate(podcast, user, any(), any()) }
    }
}
