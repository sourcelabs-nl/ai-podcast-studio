package com.aisummarypodcast.tts

import com.aisummarypodcast.store.PodcastStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ElevenLabsTtsProvider(
    private val apiClient: ElevenLabsApiClient
) : TtsProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    override val maxChunkSize: Int = 5000

    override fun scriptGuidelines(style: PodcastStyle, pronunciations: Map<String, String>): String =
        "You MAY include emotion cues in square brackets to guide vocal delivery (e.g., [cheerfully], [seriously], [with excitement]). Keep cues natural and sparse."

    override suspend fun generate(request: TtsRequest): TtsResult {
        val voiceId = request.ttsVoices["default"]
            ?: throw IllegalStateException("ElevenLabs TTS requires a 'default' voice in ttsVoices")

        val chunks = TextChunker.chunk(request.script, maxChunkSize)
        log.info("Generating ElevenLabs TTS audio for {} chunks (voice: {}, language: {})", chunks.size, voiceId, request.language)

        val voiceSettings = request.ttsSettings.ifEmpty { null }
        val totalCharacters = chunks.sumOf { it.length }

        val audioChunks = withContext(Dispatchers.IO) {
            chunks.mapIndexed { index, chunk ->
                log.info("Generating ElevenLabs TTS chunk {}/{} ({} chars)", index + 1, chunks.size, chunk.length)
                apiClient.textToSpeech(request.userId, voiceId, chunk, voiceSettings)
                    .also { request.progress?.onChunkCompleted(index + 1, chunks.size) }
            }
        }

        // eleven_v3 is dialogue-only; single-speaker TTS uses eleven_flash_v2_5
        return TtsResult(audioChunks, totalCharacters, requiresConcatenation = chunks.size > 1, model = "eleven_flash_v2_5")
    }
}
