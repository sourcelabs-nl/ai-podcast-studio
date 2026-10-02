package com.aisummarypodcast.research

import com.aisummarypodcast.store.ApiKeyCategory
import com.aisummarypodcast.user.UserProviderConfigService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper

/**
 * Caching facade over [TavilyClient]. Identical (query, maxResults) pairs are served from
 * [ResearchCacheRepository] so re-runs of compose are deterministic and free.
 */
@Service
class ResearchService(
    private val tavilyClient: TavilyClient,
    private val cacheRepository: ResearchCacheRepository,
    private val objectMapper: ObjectMapper,
    private val providerConfigService: UserProviderConfigService,
    private val restClientBuilder: RestClient.Builder
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun search(userId: String, query: String, maxResults: Int): TavilyResponse {
        val cached = cacheRepository.find(query, maxResults)
        if (cached != null) {
            return try {
                objectMapper.readValue(cached, TavilyResponse::class.java)
            } catch (e: Exception) {
                log.warn("[Research] Failed to parse cached response, refetching: {}", e.message)
                fetchAndCache(userId, query, maxResults)
            }
        }
        return fetchAndCache(userId, query, maxResults)
    }

    /** Sends one small search to Tavily with the user's resolved key, reporting the outcome instead of throwing. */
    fun testTavily(userId: String): ResearchTestResult {
        val config = providerConfigService.resolveConfig(userId, ApiKeyCategory.RESEARCH, "tavily")
        val apiKey = config?.apiKey
        if (apiKey.isNullOrBlank()) {
            return ResearchTestResult(
                success = false,
                message = "No Tavily API key configured (neither user-stored key nor TAVILY_API_KEY env var)."
            )
        }

        return try {
            val client = restClientBuilder.baseUrl(config.baseUrl).build()
            val response = client.post()
                .uri("/search")
                .header("Authorization", "Bearer $apiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("query" to "AI podcast deep dive test", "max_results" to 1, "search_depth" to "basic"))
                .retrieve()
                .body(Map::class.java)

            @Suppress("UNCHECKED_CAST")
            val results = (response?.get("results") as? List<Map<String, Any?>>).orEmpty()
            ResearchTestResult(
                success = true,
                message = "Tavily reachable. Returned ${results.size} result(s).",
                resultCount = results.size
            )
        } catch (e: Exception) {
            log.warn("[Research] Tavily test failed for user '{}': {}", userId, e.message)
            ResearchTestResult(
                success = false,
                message = "Tavily request failed: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    private fun fetchAndCache(userId: String, query: String, maxResults: Int): TavilyResponse {
        val response = tavilyClient.search(userId, query, maxResults)
        if (response.results.isNotEmpty()) {
            try {
                cacheRepository.save(query, maxResults, objectMapper.writeValueAsString(response))
            } catch (e: Exception) {
                log.warn("[Research] Failed to cache response for query '{}': {}", query, e.message)
            }
        }
        return response
    }
}
