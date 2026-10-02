package com.aisummarypodcast.tts

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.podcast.StaticFeedExporter
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeRepository
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.Podcast
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Optional

class TtsPipelineTest {

    @TempDir
    lateinit var episodesDir: Path

    private val provider = mockk<TtsProvider>()
    private val factory = mockk<TtsProviderFactory> { every { resolve(any()) } returns provider }
    private val audioConcatenator = mockk<AudioConcatenator>(relaxed = true)
    private val audioDuration = mockk<AudioDuration> { every { calculate(any()) } returns 42 }
    private val episodeRepository = mockk<EpisodeRepository>()
    private val staticFeedExporter = mockk<StaticFeedExporter>()
    private val appProperties = mockk<AppProperties>(relaxed = true)

    private val podcast = Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech")
    private val episode = Episode(
        id = 5L, podcastId = "p1", generatedAt = "2026-10-01T06:00:00Z", scriptText = "Script",
        status = EpisodeStatus.GENERATING_AUDIO
    )

    private fun pipeline(): TtsPipeline {
        every { appProperties.episodes.directory } returns episodesDir.toString()
        coEvery { provider.generate(any()) } returns TtsResult(audioChunks = emptyList(), totalCharacters = 6, model = "tts-model")
        every { episodeRepository.findById(5L) } returns Optional.of(episode)
        every { episodeRepository.save(any()) } answers { firstArg() }
        return TtsPipeline(factory, audioConcatenator, audioDuration, episodeRepository, appProperties, staticFeedExporter)
    }

    @Test
    fun `a failing feed export does not fail an episode whose audio was generated`() = runTest {
        every { staticFeedExporter.export(podcast) } throws RuntimeException("disk full")

        val result = pipeline().generateForExistingEpisode(episode, podcast)

        assertEquals(EpisodeStatus.GENERATED, result.status)
        assertEquals(42, result.durationSeconds)
    }

    @Test
    fun `a failing feed export does not fail a newly generated episode`() = runTest {
        every { staticFeedExporter.export(podcast) } throws RuntimeException("disk full")

        val result = pipeline().generate("Script", podcast)

        assertEquals(42, result.durationSeconds)
    }
}
