package com.aisummarypodcast.tts

import com.aisummarypodcast.store.ApiKeyCategory
import com.aisummarypodcast.user.UserProviderConfigService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.client.ReactorClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import io.netty.resolver.DefaultAddressResolverGroup
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
import java.time.Duration
import java.util.Base64

data class InworldSpeechResponse(
    val audioContent: String,
    val processedCharactersCount: Int
)

data class InworldSynthesisOptions(
    val speed: Double? = null,
    val temperature: Double? = null,
    val deliveryMode: String? = null
)

@Component
class InworldApiClient(
    private val providerConfigService: UserProviderConfigService,
    private val restClientBuilder: RestClient.Builder
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // Evict idle pooled connections before Inworld's load balancer closes them server-side,
    // otherwise reusing a stale keep-alive connection fails with "Connection reset"
    private val connectionProvider = ConnectionProvider.builder("inworld-tts")
        .maxIdleTime(Duration.ofSeconds(30))
        .build()

    fun synthesizeSpeech(userId: String, voiceId: String, text: String, modelId: String, options: InworldSynthesisOptions = InworldSynthesisOptions()): InworldSpeechResponse {
        val client = createClient(userId)

        val audioConfig = mutableMapOf<String, Any>(
            "audioEncoding" to "MP3",
            "sampleRateHertz" to 48000,
            "bitRateHertz" to 128000
        )
        options.speed?.let { audioConfig["speakingRate"] = it }

        val body = mutableMapOf<String, Any>(
            "text" to text,
            "voiceId" to voiceId,
            "modelId" to modelId,
            "audioConfig" to audioConfig
        )
        if (options.deliveryMode != null) {
            body["deliveryMode"] = options.deliveryMode
        } else {
            options.temperature?.let { body["temperature"] = it }
        }
        body["applyTextNormalization"] = "ON"

        @Suppress("UNCHECKED_CAST")
        val response = client.post()
            .uri("/tts/v1/voice")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { _, resp ->
                handleError(resp.statusCode, resp.body.readAllBytes())
            }
            .body(Map::class.java)
            ?: throw IllegalStateException("Empty response from Inworld TTS API")

        val audioContent = response["audioContent"] as? String
            ?: throw IllegalStateException("Missing audioContent in Inworld TTS response")

        val usage = response["usage"] as? Map<String, Any?>
        val processedChars = (usage?.get("processedCharactersCount") as? Number)?.toInt() ?: text.length

        return InworldSpeechResponse(audioContent, processedChars)
    }

    fun listVoices(userId: String): List<VoiceInfo> {
        val client = createClient(userId)

        @Suppress("UNCHECKED_CAST")
        val response = client.get()
            .uri("/tts/v1/voices")
            .retrieve()
            .onStatus(HttpStatusCode::isError) { _, resp ->
                handleError(resp.statusCode, resp.body.readAllBytes())
            }
            .body(Map::class.java)
            ?: throw IllegalStateException("Empty response from Inworld voices API")

        val voices = response["voices"] as? List<Map<String, Any?>> ?: emptyList()
        return voices.map { voice ->
            val isCustom = voice["isCustom"] as? Boolean ?: false
            VoiceInfo(
                voiceId = voice["voiceId"] as? String ?: "",
                name = voice["displayName"] as? String ?: "",
                category = if (isCustom) "cloned" else "premade",
                previewUrl = voice["previewUrl"] as? String
            )
        }
    }

    internal fun createClient(userId: String): RestClient {
        val config = providerConfigService.resolveConfig(userId, ApiKeyCategory.TTS, "inworld")
            ?: throw IllegalStateException("No Inworld provider config found. Configure Inworld API credentials (INWORLD_AI_JWT_KEY and INWORLD_AI_JWT_SECRET).")

        val apiKey = config.apiKey
            ?: throw IllegalStateException("Inworld API credentials must be configured")

        val basicToken = buildBasicToken(apiKey)

        val httpClient = HttpClient.create(connectionProvider)
            .resolver(DefaultAddressResolverGroup.INSTANCE)
            .responseTimeout(Duration.ofMinutes(5))
        val requestFactory = ReactorClientHttpRequestFactory(httpClient)

        return restClientBuilder
            .requestFactory(requestFactory)
            .baseUrl(config.baseUrl)
            .defaultHeader("Authorization", "Basic $basicToken")
            .build()
    }

    internal fun buildBasicToken(credentials: String): String {
        return Base64.getEncoder().encodeToString(credentials.toByteArray())
    }

    private fun handleError(status: HttpStatusCode, body: ByteArray) {
        val bodyStr = String(body)
        when (status.value()) {
            401 -> throw IllegalStateException("Inworld API credentials are invalid or expired")
            429 -> throw InworldRateLimitException("Inworld rate limit exceeded. Please try again later.")
            else -> {
                log.error("Inworld API error (HTTP {}): {}", status.value(), bodyStr)
                throw IllegalStateException("Inworld API error (HTTP ${status.value()}): $bodyStr")
            }
        }
    }
}
