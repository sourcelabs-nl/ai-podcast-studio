package com.aisummarypodcast.tts

import com.aisummarypodcast.store.ApiKeyCategory
import com.aisummarypodcast.user.UserProviderConfigService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration

data class DialogueInput(val text: String, val voice_id: String)

data class VoiceInfo(val voiceId: String, val name: String, val category: String, val previewUrl: String?)

@Component
class ElevenLabsApiClient(
    private val providerConfigService: UserProviderConfigService,
    private val restClientBuilder: RestClient.Builder
) {

    private val log = LoggerFactory.getLogger(javaClass)

    init {
        // Audio synthesis can take minutes, so override the global 30s read timeout with 5m.
        // Configured once on this client's own builder (not per call) so tests can still bind a
        // MockRestServiceServer factory afterwards.
        restClientBuilder.requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(10))
                setReadTimeout(Duration.ofMinutes(5))
            }
        )
    }

    fun textToSpeech(userId: String, voiceId: String, text: String, voiceSettings: Map<String, String>?): ByteArray {
        val client = createClient(userId)
        val body = buildMap<String, Any> {
            put("text", text)
            // eleven_v3 is dialogue-only; single-speaker TTS uses eleven_flash_v2_5
            put("model_id", "eleven_flash_v2_5")
            if (!voiceSettings.isNullOrEmpty()) {
                put("voice_settings", voiceSettings.mapValues { (_, v) -> v.toDoubleOrNull() ?: v })
            }
        }

        return client.post()
            .uri("/v1/text-to-speech/{voiceId}?output_format=mp3_44100_128", voiceId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { _, response ->
                handleError(response.statusCode, response.body.readAllBytes())
            }
            .body(ByteArray::class.java)
            ?: throw IllegalStateException("Empty response from ElevenLabs TTS API")
    }

    fun textToDialogue(userId: String, inputs: List<DialogueInput>, settings: Map<String, String>?): ByteArray {
        val client = createClient(userId)
        val body = buildMap<String, Any> {
            put("inputs", inputs)
            put("model_id", "eleven_v3")
            if (!settings.isNullOrEmpty()) {
                put("settings", settings.mapValues { (_, v) -> v.toDoubleOrNull() ?: v })
            }
        }

        return client.post()
            .uri("/v1/text-to-dialogue?output_format=mp3_44100_128")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { _, response ->
                handleError(response.statusCode, response.body.readAllBytes())
            }
            .body(ByteArray::class.java)
            ?: throw IllegalStateException("Empty response from ElevenLabs Text-to-Dialogue API")
    }

    fun listVoices(userId: String): List<VoiceInfo> {
        val client = createClient(userId)

        val response = client.get()
            .uri("/v1/voices")
            .retrieve()
            .onStatus(HttpStatusCode::isError) { _, resp ->
                handleError(resp.statusCode, resp.body.readAllBytes())
            }
            .body(Map::class.java)
            ?: throw IllegalStateException("Empty response from ElevenLabs voices API")

        @Suppress("UNCHECKED_CAST")
        val voices = response["voices"] as? List<Map<String, Any?>> ?: emptyList()
        return voices.map { voice ->
            VoiceInfo(
                voiceId = voice["voice_id"] as? String ?: "",
                name = voice["name"] as? String ?: "",
                category = voice["category"] as? String ?: "",
                previewUrl = voice["preview_url"] as? String
            )
        }
    }

    private fun createClient(userId: String): RestClient {
        val config = providerConfigService.resolveConfig(userId, ApiKeyCategory.TTS, "elevenlabs")
            ?: throw IllegalStateException("No ElevenLabs provider config found. Configure an ElevenLabs API key for TTS.")

        return restClientBuilder
            .baseUrl(config.baseUrl)
            .defaultHeader("xi-api-key", config.apiKey ?: "")
            .build()
    }

    private fun handleError(status: HttpStatusCode, body: ByteArray) {
        val bodyStr = String(body)
        when (status.value()) {
            401 -> {
                if (bodyStr.contains("quota_exceeded")) {
                    log.error("ElevenLabs quota exceeded: {}", bodyStr)
                    throw IllegalStateException("ElevenLabs quota exceeded. Check your plan credits.")
                }
                throw IllegalStateException("ElevenLabs API key is invalid or expired")
            }
            429 -> throw IllegalStateException("ElevenLabs rate limit exceeded. Please try again later.")
            else -> {
                log.error("ElevenLabs API error (HTTP {}): {}", status.value(), bodyStr)
                throw IllegalStateException("ElevenLabs API error (HTTP ${status.value()}): $bodyStr")
            }
        }
    }
}
