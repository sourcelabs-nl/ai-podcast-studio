package com.aisummarypodcast.tts

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.llm.CostEstimator
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path

/**
 * Turns a previewed script into audio without creating an episode. A preview belongs to no episode,
 * so nothing here is persisted to the database and the TTS cost is deliberately not recorded — it
 * mirrors how the script preview itself behaves.
 *
 * Two shapes, because the timings differ by two orders of magnitude. A sample is one chunk and
 * returns in seconds, so its bytes go straight back in the response. Full audio is dozens of chunks
 * and runs for minutes, so it is written to the preview store and fetched separately.
 */
@Service
class PreviewAudioService(
    private val ttsProviderFactory: TtsProviderFactory,
    private val previewAudioStore: PreviewAudioStore,
    private val audioConcatenator: AudioConcatenator,
    private val appProperties: AppProperties
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private companion object {
        val MULTI_VOICE_STYLES = setOf(PodcastStyle.DIALOGUE, PodcastStyle.INTERVIEW)
    }

    /**
     * Synthesises the opening slice of [script] and returns the MP3 bytes. The slice goes through
     * the same provider path a real episode uses, so delivery mode, steering, synthesis context,
     * pronunciations, and post-processing all apply and the sample sounds like the episode will.
     */
    suspend fun synthesizeSample(podcast: Podcast, script: String): ByteArray {
        val provider = ttsProviderFactory.resolve(podcast)
        val sample = PreviewSampleSelector.select(script, podcast.style, provider.maxChunkSize)
        require(sample.text.isNotBlank()) { "Script contains no speakable text" }

        log.info(
            "Synthesising preview sample for podcast '{}' ({}): {} chars, roles {}",
            podcast.name, podcast.id, sample.text.length, sample.roles
        )
        val result = provider.generate(TtsRequest.forPodcast(podcast, sample.text))
        return toSingleFile(result)
    }

    /**
     * Synthesises the whole [script] and stores it, returning the opaque id it is reachable under.
     * [onProgress] is invoked as each chunk completes so a caller streaming to a client can report
     * how far along the run is.
     */
    suspend fun generateFullAudio(podcast: Podcast, script: String, onProgress: TtsProgressListener): String {
        require(script.isNotBlank()) { "Script contains no speakable text" }
        val provider = ttsProviderFactory.resolve(podcast)

        log.info("Generating full preview audio for podcast '{}' ({}): {} chars", podcast.name, podcast.id, script.length)
        val result = provider.generate(TtsRequest.forPodcast(podcast, script, onProgress))

        return withContext(Dispatchers.IO) {
            previewAudioStore.write(podcast.id, result.audioChunks, result.requiresConcatenation)
        }
    }

    /** The stored preview audio, or null when the id is unknown or is not this podcast's. */
    fun findStoredAudio(podcastId: String, audioId: String): Path? = previewAudioStore.find(podcastId, audioId)

    fun sweepExpiredAudio(): Int = previewAudioStore.sweepExpired()

    /**
     * What a full synthesis of [script] would bill. The configured model is used when the podcast
     * names one; otherwise the provider's first configured rate stands in, the same fallback the
     * episode pipeline lands on.
     */
    fun estimate(podcast: Podcast, script: String): PreviewAudioEstimate {
        val characters = billableCharacters(podcast, script)
        val costCents = CostEstimator.estimateTtsCostCents(
            characters,
            appProperties.models,
            podcast.ttsProvider.value,
            podcast.ttsSettings?.get("model")
        )
        return PreviewAudioEstimate(characters, costCents)
    }

    /**
     * Speaker tags route a turn to a voice and are never sent to the provider, so a dialogue script
     * bills only the spoken text inside them.
     */
    private fun billableCharacters(podcast: Podcast, script: String): Int {
        val sanitized = TtsScriptSanitizer.sanitize(script)
        if (podcast.style !in MULTI_VOICE_STYLES) return sanitized.length
        val turns = DialogueScriptParser.parse(sanitized)
        return if (turns.isEmpty()) sanitized.length else turns.sumOf { it.text.length }
    }

    /**
     * A multi-chunk sample still has to arrive as one playable file. It is concatenated through a
     * throwaway temp file because ffmpeg writes to a path, and nothing about a sample is kept.
     */
    private suspend fun toSingleFile(result: TtsResult): ByteArray = withContext(Dispatchers.IO) {
        if (!result.requiresConcatenation) return@withContext result.audioChunks.first()
        val temp = Files.createTempFile("preview-sample", ".mp3")
        try {
            audioConcatenator.concatenate(result.audioChunks, temp)
            Files.readAllBytes(temp)
        } finally {
            Files.deleteIfExists(temp)
        }
    }
}
