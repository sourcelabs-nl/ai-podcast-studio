package com.aisummarypodcast.tts

import com.aisummarypodcast.llm.buildOpenAiClient
import com.aisummarypodcast.store.ApiKeyCategory
import com.aisummarypodcast.store.PodcastStyle
import com.aisummarypodcast.user.UserProviderConfigService
import org.slf4j.LoggerFactory
import org.springframework.ai.audio.tts.TextToSpeechPrompt
import org.springframework.ai.openai.OpenAiAudioSpeechModel
import org.springframework.ai.openai.OpenAiAudioSpeechOptions
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class OpenAiTtsProvider(
    private val providerConfigService: UserProviderConfigService
) : TtsProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    override val maxChunkSize: Int = 4096

    override fun scriptGuidelines(style: PodcastStyle, pronunciations: Map<String, String>): String =
        "Write clean, natural speech. Do not use special markup, emotion tags, sound effects, or non-verbal cues — the TTS engine does not support them."

    override fun generate(request: TtsRequest): TtsResult {
        val voice = request.ttsVoices["default"]
            ?: throw IllegalStateException("OpenAI TTS requires a 'default' voice in ttsVoices")
        val speed = request.ttsSettings["speed"]?.toDoubleOrNull() ?: 1.0

        val chunks = TextChunker.chunk(request.script, maxChunkSize)
        log.info("Generating OpenAI TTS audio for {} chunks (voice: {}, speed: {}, language: {})", chunks.size, voice, speed, request.language)

        val speechModel = createSpeechModel(request.userId)

        val options = OpenAiAudioSpeechOptions.builder()
            .voice(voice)
            .speed(speed)
            .build()

        val totalCharacters = chunks.sumOf { it.length }

        val audioChunks = chunks.mapIndexed { index, chunk ->
            log.info("Generating TTS chunk {}/{} ({} chars)", index + 1, chunks.size, chunk.length)
            val response = speechModel.call(TextToSpeechPrompt(chunk, options))
            response.result.output
        }

        return TtsResult(audioChunks, totalCharacters, requiresConcatenation = chunks.size > 1, model = "tts-1")
    }

    private fun createSpeechModel(userId: String): OpenAiAudioSpeechModel {
        val config = providerConfigService.resolveConfig(userId, ApiKeyCategory.TTS, "openai")
            ?: throw IllegalStateException("No provider config available for OpenAI TTS. Configure a user provider or set the OPENAI_API_KEY environment variable.")

        val client = buildOpenAiClient(config, Duration.ofMinutes(5))
        return OpenAiAudioSpeechModel.builder()
            .openAiClient(client)
            .build()
    }
}
