package com.aisummarypodcast.llm

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

data class PastEpisodeMatch(
    val episodeId: Long,
    val generatedAt: String,
    val topics: String,
    val recapSnippet: String
)

@Repository
class EpisodeHistoryRepository(
    private val jdbcClient: JdbcClient
) {

    fun search(podcastId: String, query: String, limit: Int = 5): List<PastEpisodeMatch> {
        val sanitized = sanitizeQuery(query)
        if (sanitized.isBlank()) return emptyList()

        return jdbcClient.sql(
            """
            SELECT episode_id, generated_at, topics, recap
            FROM episode_history_fts
            WHERE episode_history_fts MATCH :query
              AND podcast_id = :podcastId
            ORDER BY bm25(episode_history_fts) ASC
            LIMIT :limit
            """.trimIndent()
        )
            .param("query", sanitized)
            .param("podcastId", podcastId)
            .param("limit", limit)
            .query { rs, _ ->
                PastEpisodeMatch(
                    episodeId = rs.getLong("episode_id"),
                    generatedAt = isoDate(rs.getString("generated_at")),
                    topics = rs.getString("topics") ?: "",
                    recapSnippet = truncateSnippet(rs.getString("recap") ?: "")
                )
            }
            .list()
    }

    private fun sanitizeQuery(query: String): String {
        val tokens = query
            .split(Regex("\\s+"))
            .map { it.replace("\"", "").trim() }
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return ""
        return tokens.joinToString(" OR ") { "\"$it\"" }
    }

    private fun isoDate(value: String): String {
        if (value.length >= 10) return value.substring(0, 10)
        return value
    }

    private fun truncateSnippet(text: String): String {
        val collapsed = text.replace(Regex("\\s+"), " ").trim()
        if (collapsed.length <= MAX_SNIPPET_CHARS) return collapsed
        return collapsed.substring(0, MAX_SNIPPET_CHARS).trimEnd() + "…"
    }

    companion object {
        private const val MAX_SNIPPET_CHARS = 280
    }
}
