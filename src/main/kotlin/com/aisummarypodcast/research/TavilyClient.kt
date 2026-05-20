package com.aisummarypodcast.research

import com.aisummarypodcast.store.ApiKeyCategory
import com.aisummarypodcast.user.UserProviderConfigService
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

data class TavilyResult(
    val title: String,
    val url: String,
    val content: String
)

data class TavilyResponse(
    val results: List<TavilyResult> = emptyList()
)

private data class TavilyRequest(
    val query: String,
    @JsonProperty("max_results") val maxResults: Int,
    @JsonProperty("search_depth") val searchDepth: String = "basic",
    @JsonProperty("include_answer") val includeAnswer: Boolean = false
)

private data class TavilyApiResponse(
    val results: List<TavilyApiResult> = emptyList()
)

private data class TavilyApiResult(
    val title: String? = null,
    val url: String? = null,
    val content: String? = null
)

/**
 * Thin Tavily search client. HTTP errors, timeouts, and missing keys surface as an empty
 * result list with a logged warning — episode generation must never fail because of a
 * Tavily issue.
 */
@Component
class TavilyClient(
    private val providerConfigService: UserProviderConfigService,
    private val restClientBuilder: RestClient.Builder
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun search(userId: String, query: String, maxResults: Int): TavilyResponse {
        val config = providerConfigService.resolveConfig(userId, ApiKeyCategory.RESEARCH, "tavily")
        val apiKey = config?.apiKey
        if (apiKey.isNullOrBlank()) {
            log.warn("[Research] No Tavily API key configured (user '{}') — returning empty results", userId)
            return TavilyResponse()
        }

        return try {
            val client = restClientBuilder.baseUrl(config.baseUrl).build()

            val apiResponse = client.post()
                .uri("/search")
                .header("Authorization", "Bearer $apiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .body(TavilyRequest(query = query, maxResults = maxResults))
                .retrieve()
                .body(TavilyApiResponse::class.java)
                ?: TavilyApiResponse()

            TavilyResponse(
                results = apiResponse.results.mapNotNull { r ->
                    val title = r.title ?: return@mapNotNull null
                    val url = r.url ?: return@mapNotNull null
                    val content = r.content ?: return@mapNotNull null
                    TavilyResult(title = title, url = url, content = content)
                }
            )
        } catch (e: Exception) {
            log.warn("[Research] Tavily search failed for query '{}': {}", query, e.message)
            TavilyResponse()
        }
    }
}
