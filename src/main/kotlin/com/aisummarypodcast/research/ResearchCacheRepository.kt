package com.aisummarypodcast.research

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.security.MessageDigest
import java.time.Instant

/**
 * Cache for Tavily search responses keyed on (query_hash, max_results). Identical queries
 * within the cache window are served without an outbound HTTP call. Results are public so
 * the cache is shared across users.
 */
@Repository
class ResearchCacheRepository(
    private val jdbcClient: JdbcClient
) {

    fun find(query: String, maxResults: Int): String? {
        val hash = hash(query)
        return jdbcClient.sql(
            "SELECT response_json FROM research_cache WHERE query_hash = :hash AND max_results = :maxResults"
        )
            .param("hash", hash)
            .param("maxResults", maxResults)
            .query(String::class.java)
            .optional()
            .orElse(null)
    }

    fun save(query: String, maxResults: Int, responseJson: String) {
        val hash = hash(query)
        jdbcClient.sql(
            """
            INSERT OR REPLACE INTO research_cache (query_hash, max_results, query, response_json, cached_at)
            VALUES (:hash, :maxResults, :query, :responseJson, :cachedAt)
            """.trimIndent()
        )
            .param("hash", hash)
            .param("maxResults", maxResults)
            .param("query", query)
            .param("responseJson", responseJson)
            .param("cachedAt", Instant.now().toString())
            .update()
    }

    private fun hash(query: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(query.trim().lowercase().toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
