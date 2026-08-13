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
    val deliveryMode: String? = null,
    /** Inworld's "Enhanced" audio quality toggle — applies denoising to reduce artifacts. */
    val enhanceGeneration: Boolean? = null,
    /** BCP-47 language tag, so Inworld uses a localized voice prompt instead of auto-detecting. */
    val language: String? = null,
    /**
     * Text of the requests that immediately precede this one, oldest first. Sent as
     * `synthesisContext.previousRequests` so intonation carries across chunk splices.
     */
    val previousRequests: List<String> = emptyList()
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

    companion object {
        /** Documented `audioConfig.speakingRate` range. */
        private const val MIN_SPEAKING_RATE = 0.5
        private const val MAX_SPEAKING_RATE = 1.5

        /** Inworld recommends staying at or above this; lower is legal but degrades quality. */
        private const val RECOMMENDED_MIN_SPEAKING_RATE = 0.8
    }

    internal fun buildSynthesisBody(voiceId: String, text: String, modelId: String, options: InworldSynthesisOptions): Map<String, Any> {
        val audioConfig = mutableMapOf<String, Any>(
            "audioEncoding" to "MP3",
            "sampleRateHertz" to 48000,
            "bitRate" to 128000
        )
        options.speed?.let { audioConfig["speakingRate"] = clampSpeakingRate(it) }

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
        options.enhanceGeneration?.let { body["enhanceGeneration"] = it }
        options.language?.takeIf { it.isNotBlank() }?.let { body["language"] = it }
        if (options.previousRequests.isNotEmpty()) {
            body["synthesisContext"] = mapOf(
                "previousRequests" to options.previousRequests.map { mapOf("text" to it) }
            )
        }
        body["applyTextNormalization"] = "ON"
        return body
    }

    internal fun clampSpeakingRate(speed: Double): Double {
        val clamped = speed.coerceIn(MIN_SPEAKING_RATE, MAX_SPEAKING_RATE)
        when {
            clamped != speed -> log.warn(
                "Inworld speakingRate {} is outside the supported range [{}, {}]; clamped to {}",
                speed, MIN_SPEAKING_RATE, MAX_SPEAKING_RATE, clamped
            )
            clamped < RECOMMENDED_MIN_SPEAKING_RATE -> log.warn(
                "Inworld speakingRate {} is below the recommended minimum of {}; audio quality may degrade",
                speed, RECOMMENDED_MIN_SPEAKING_RATE
            )
        }
        return clamped
    }

    fun synthesizeSpeech(userId: String, voiceId: String, text: String, modelId: String, options: InworldSynthesisOptions = InworldSynthesisOptions()): InworldSpeechResponse {
        val client = createClient(userId)
        val body = buildSynthesisBody(voiceId, text, modelId, options)

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
        when (val code = status.value()) {
            401 -> throw IllegalStateException("Inworld API credentials are invalid or expired")
            429 -> throw InworldRateLimitException("Inworld rate limit exceeded. Please try again later.")
            in 500..599 -> {
                log.error("Inworld API error (HTTP {}): {}", code, bodyStr)
                throw InworldTransientException("Inworld API error (HTTP $code): $bodyStr")
            }
            else -> {
                log.error("Inworld API error (HTTP {}): {}", code, bodyStr)
                throw IllegalStateException("Inworld API error (HTTP $code): $bodyStr")
            }
        }
    }
}
