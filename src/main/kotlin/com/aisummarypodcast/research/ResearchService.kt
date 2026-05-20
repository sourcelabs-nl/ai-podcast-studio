package com.aisummarypodcast.research

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

/**
 * Caching facade over [TavilyClient]. Identical (query, maxResults) pairs are served from
 * [ResearchCacheRepository] so re-runs of compose are deterministic and free.
 */
@Service
class ResearchService(
    private val tavilyClient: TavilyClient,
    private val cacheRepository: ResearchCacheRepository,
    private val objectMapper: ObjectMapper
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
