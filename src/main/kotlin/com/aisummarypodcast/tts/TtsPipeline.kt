package com.aisummarypodcast.tts

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.llm.CostEstimator
import com.aisummarypodcast.podcast.StaticFeedExporter
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeRepository
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.Podcast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class AudioOutput(val path: Path, val durationSeconds: Int)

@Component
class TtsPipeline(
    private val ttsProviderFactory: TtsProviderFactory,
    private val audioConcatenator: AudioConcatenator,
    private val audioDuration: AudioDuration,
    private val episodeRepository: EpisodeRepository,
    private val appProperties: AppProperties,
    private val staticFeedExporter: StaticFeedExporter
) {

    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun generate(script: String, podcast: Podcast): Episode {
        log.info("[TTS] Starting audio generation for podcast '{}' ({}) (provider: {})", podcast.name, podcast.id, podcast.ttsProvider)

        val ttsResult = callProvider(script, podcast)
        val ttsCostCents = CostEstimator.estimateTtsCostCents(ttsResult.totalCharacters, appProperties.models, podcast.ttsProvider.value, ttsResult.model)

        val audioOutput = generateAudioFile(ttsResult, podcast)

        val episode = episodeRepository.save(
            Episode(
                podcastId = podcast.id,
                generatedAt = Instant.now().toString(),
                scriptText = script,
                audioFilePath = audioOutput.path.toString(),
                durationSeconds = audioOutput.durationSeconds,
                ttsCharacters = ttsResult.totalCharacters,
                ttsCostCents = ttsCostCents,
                ttsModel = ttsResult.model,
                ttsCalls = ttsResult.audioChunks.size
            )
        )

        log.info("[TTS] Episode generated for podcast '{}' ({}): {} ({} seconds)", podcast.name, podcast.id, audioOutput.path.fileName, audioOutput.durationSeconds)
        withContext(Dispatchers.IO) { staticFeedExporter.export(podcast) }
        return episode
    }

    suspend fun generateForExistingEpisode(episode: Episode, podcast: Podcast): Episode {
        log.info("[TTS] Starting audio generation for episode {} (podcast '{}' ({}), provider: {})", episode.id, podcast.name, podcast.id, podcast.ttsProvider)

        val ttsResult = callProvider(episode.scriptText, podcast)
        val ttsCostCents = CostEstimator.estimateTtsCostCents(ttsResult.totalCharacters, appProperties.models, podcast.ttsProvider.value, ttsResult.model)

        val audioOutput = generateAudioFile(ttsResult, podcast)

        val fresh = episodeRepository.findByIdOrNull(episode.id!!) ?: episode
        val updated = episodeRepository.save(
            fresh.copy(
                status = EpisodeStatus.GENERATED,
                audioFilePath = audioOutput.path.toString(),
                durationSeconds = audioOutput.durationSeconds,
                ttsCharacters = ttsResult.totalCharacters,
                ttsCostCents = ttsCostCents,
                ttsModel = ttsResult.model,
                ttsCalls = ttsResult.audioChunks.size
            )
        )

        log.info("[TTS] Episode {} audio generated for podcast '{}' ({}): {} ({} seconds)", episode.id, podcast.name, podcast.id, audioOutput.path.fileName, audioOutput.durationSeconds)
        withContext(Dispatchers.IO) { staticFeedExporter.export(podcast) }
        return updated
    }

    private suspend fun callProvider(script: String, podcast: Podcast): TtsResult {
        val provider = ttsProviderFactory.resolve(podcast)
        val request = TtsRequest(
            script = TtsScriptSanitizer.sanitize(script),
            ttsVoices = podcast.ttsVoices ?: mapOf("default" to "nova"),
            ttsSettings = podcast.ttsSettings ?: emptyMap(),
            language = podcast.language,
            userId = podcast.userId
        )
        return provider.generate(request)
    }

    private suspend fun generateAudioFile(ttsResult: TtsResult, podcast: Podcast): AudioOutput = withContext(Dispatchers.IO) {
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())
        val fileName = "briefing-$timestamp.mp3"
        val episodesDir = Path.of(appProperties.episodes.directory, podcast.id, "episodes")
        Files.createDirectories(episodesDir)
        val outputPath = episodesDir.resolve(fileName)

        if (ttsResult.requiresConcatenation) {
            audioConcatenator.concatenate(ttsResult.audioChunks, outputPath)
        } else {
            Files.write(outputPath, ttsResult.audioChunks.first())
        }

        val duration = audioDuration.calculate(outputPath)
        AudioOutput(outputPath, duration)
    }
}
