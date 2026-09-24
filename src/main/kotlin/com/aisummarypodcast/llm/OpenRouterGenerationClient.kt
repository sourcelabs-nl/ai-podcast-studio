package com.aisummarypodcast.llm

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient

/**
 * Reads OpenRouter's account of one request from `GET /api/v1/generation?id=…`.
 *
 * The endpoint answers 404 until OpenRouter has finished accounting for the request, which a probe
 * measured at 10-20 seconds after it returned, so [fetch] returns null for that case and leaves
 * retrying to the caller. Every other failure is thrown.
 */
@Component
class OpenRouterGenerationClient(
    private val restClientBuilder: RestClient.Builder
) {

    fun fetch(generationId: String, lookup: GenerationStatsLookup): GenerationStats? {
        val response = try {
            restClientBuilder.clone().baseUrl(openAiApiBaseUrl(lookup.baseUrl)).build()
                .get()
                .uri { it.path("/generation").queryParam("id", generationId).build() }
                .header("Authorization", "Bearer ${lookup.apiKey}")
                .retrieve()
                .body(GenerationApiResponse::class.java)
        } catch (e: HttpClientErrorException) {
            if (e.statusCode == HttpStatus.NOT_FOUND) return null
            throw e
        }
        return response?.data?.toStats()
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class GenerationApiResponse(val data: GenerationData? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class GenerationData(
    val latency: Long? = null,
    @JsonProperty("generation_time") val generationTime: Long? = null,
    @JsonProperty("native_tokens_completion") val nativeTokensCompletion: Int? = null,
    @JsonProperty("native_tokens_reasoning") val nativeTokensReasoning: Int? = null,
    @JsonProperty("finish_reason") val finishReason: String? = null,
    @JsonProperty("provider_name") val providerName: String? = null,
    @JsonProperty("provider_responses") val providerResponses: List<ProviderResponse>? = null
) {
    fun toStats() = GenerationStats(
        firstContentMs = latency,
        generationTimeMs = generationTime,
        nativeCompletionTokens = nativeTokensCompletion,
        nativeReasoningTokens = nativeTokensReasoning,
        finishReason = finishReason,
        servedProvider = providerName,
        attempts = providerResponses.orEmpty().map { ProviderAttempt(it.providerName, it.status, it.latency) }
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class ProviderResponse(
    @JsonProperty("provider_name") val providerName: String? = null,
    val status: Int? = null,
    val latency: Long? = null
)
