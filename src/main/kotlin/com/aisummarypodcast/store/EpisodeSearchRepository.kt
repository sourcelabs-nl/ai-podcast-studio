package com.aisummarypodcast.store

import org.springframework.data.domain.Pageable
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/** A page of episode ids matching a search, newest first, with the total across all pages. */
data class EpisodeSearchPage(val episodeIds: List<Long>, val total: Long)

/** Why one episode matched: the covered topics and article titles that contain a query term. */
data class EpisodeMatchDetails(val topics: List<String>, val articleTitles: List<String>)

interface EpisodeSearchRepository {

    /**
     * Ids of the episodes matching every term in [terms], ordered newest first.
     *
     * A term matches as a case-insensitive substring of the episode's own text or of one of its
     * covered stories. Terms are AND-ed, so a multi-word query narrows; they need not match the
     * same field, so "retrieval augmented" can match a topic and the script separately.
     */
    fun searchEpisodeIds(
        podcastId: String,
        statuses: Collection<EpisodeStatus>,
        terms: List<String>,
        pageable: Pageable
    ): EpisodeSearchPage

    /**
     * Per-episode matching topics and article titles, each list capped at [limitPerEpisode].
     *
     * A topic or title is included when it contains ANY term: the episode as a whole has already
     * been gated on ALL terms, so listing a per-field hit here is what explains the match.
     */
    fun findMatchDetails(episodeIds: List<Long>, terms: List<String>, limitPerEpisode: Int): Map<Long, EpisodeMatchDetails>
}

@Repository
class EpisodeSearchRepositoryImpl(
    private val jdbcClient: JdbcClient
) : EpisodeSearchRepository {

    override fun searchEpisodeIds(
        podcastId: String,
        statuses: Collection<EpisodeStatus>,
        terms: List<String>,
        pageable: Pageable
    ): EpisodeSearchPage {
        val where = buildWhere(statuses, terms)
        val params = buildParams(podcastId, statuses, terms)

        val total = jdbcClient.sql("SELECT COUNT(*) FROM episodes e WHERE $where")
            .params(params)
            .query(Long::class.java)
            .single()
        if (total == 0L) return EpisodeSearchPage(emptyList(), 0)

        val ids = jdbcClient.sql(
            """
            SELECT e.id FROM episodes e
            WHERE $where
            ORDER BY e.generated_at DESC, e.id DESC
            LIMIT :limit OFFSET :offset
            """.trimIndent()
        )
            .params(params)
            .param("limit", pageable.pageSize)
            .param("offset", pageable.offset)
            .query(Long::class.java)
            .list()
            .filterNotNull()

        return EpisodeSearchPage(ids, total)
    }

    override fun findMatchDetails(
        episodeIds: List<Long>,
        terms: List<String>,
        limitPerEpisode: Int
    ): Map<Long, EpisodeMatchDetails> {
        if (episodeIds.isEmpty() || terms.isEmpty()) return emptyMap()

        // Any term hitting either column makes the row worth showing; ALL-terms gating happened
        // at the episode level, so a per-row AND here would hide the topic that explains the match.
        val topicMatches = terms.indices.joinToString(" OR ") { matches("COALESCE(ea.topic, '')", it) }
        val articleMatches = terms.indices.joinToString(" OR ") { articleTextMatches(it) }

        val rows = jdbcClient.sql(
            """
            SELECT ea.episode_id, ea.topic, a.title,
                   ($topicMatches) AS topic_hit,
                   ($articleMatches) AS article_hit
            FROM episode_articles ea
            JOIN articles a ON a.id = ea.article_id
            WHERE ea.episode_id IN (:episodeIds)
              AND ea.topic_order IS NOT NULL
              AND (($topicMatches) OR ($articleMatches))
            ORDER BY ea.episode_id, ea.topic_order
            """.trimIndent()
        )
            .param("episodeIds", episodeIds)
            .params(terms.mapIndexed { index, term -> "t$index" to wordPattern(term) as Any }.toMap())
            .query { rs, _ ->
                MatchRow(
                    episodeId = rs.getLong("episode_id"),
                    topic = rs.getString("topic"),
                    title = rs.getString("title"),
                    topicHit = rs.getBoolean("topic_hit"),
                    articleHit = rs.getBoolean("article_hit")
                )
            }
            .list()

        return rows.groupBy { it.episodeId }.mapValues { (_, episodeRows) ->
            EpisodeMatchDetails(
                topics = episodeRows.filter { it.topicHit }.mapNotNull { it.topic }.distinct().take(limitPerEpisode),
                articleTitles = episodeRows.filter { it.articleHit }.map { it.title }.distinct().take(limitPerEpisode)
            )
        }
    }

    private fun buildWhere(statuses: Collection<EpisodeStatus>, terms: List<String>): String {
        val clauses = mutableListOf("e.podcast_id = :podcastId")
        if (statuses.isNotEmpty()) clauses += "e.status IN (:statuses)"
        terms.indices.mapTo(clauses) { termClause(it) }
        return clauses.joinToString(" AND ")
    }

    /**
     * One term, matched against the episode's own text or any of its covered stories. `topic_order`
     * marks a story as placed in the running order, which is what keeps a merely gathered article
     * from making the episode match something no listener heard.
     */
    private fun termClause(index: Int): String = """
        (
            ${matches("e.script_text", index)}
            OR ${matches("COALESCE(e.recap, '')", index)}
            OR ${matches("COALESCE(e.show_notes, '')", index)}
            OR EXISTS (
                SELECT 1 FROM episode_articles ea
                JOIN articles a ON a.id = ea.article_id
                WHERE ea.episode_id = e.id
                  AND ea.topic_order IS NOT NULL
                  AND (${matches("COALESCE(ea.topic, '')", index)} OR ${articleTextMatches(index)})
            )
        )
    """.trimIndent()

    /**
     * A covered article matches on its headline, its LLM-written summary, or its body. All three are
     * needed: an article's subject often appears only in the text, so matching the headline alone
     * reports "mentioned in the script only" for an episode whose source material plainly discusses
     * the term.
     */
    private fun articleTextMatches(index: Int): String =
        listOf("a.title", "COALESCE(a.summary, '')", "a.body")
            .joinToString(" OR ") { matches(it, index) }

    private fun buildParams(
        podcastId: String,
        statuses: Collection<EpisodeStatus>,
        terms: List<String>
    ): Map<String, Any> = buildMap {
        put("podcastId", podcastId)
        if (statuses.isNotEmpty()) put("statuses", statuses.map { it.name })
        terms.forEachIndexed { index, term -> put("t$index", wordPattern(term)) }
    }

    private data class MatchRow(
        val episodeId: Long,
        val topic: String?,
        val title: String,
        val topicHit: Boolean,
        val articleHit: Boolean
    )

    companion object {
        /**
         * Whole-word match of term [index] against [column].
         *
         * `GLOB` rather than `LIKE` because only its `[^a-z]` character class can express a word
         * boundary, and without one "java" matches "javascript", which on this archive accounted for
         * most of the hits. Note the boundary excludes letters but not digits, so "qwen" still finds
         * "Qwen3.8" while "java" no longer finds "JavaScript". The column is padded with spaces so a
         * term at the very start or end of the text still has a boundary on both sides, and lowered
         * because `GLOB` is case-sensitive.
         */
        private fun matches(column: String, index: Int): String =
            "(' ' || LOWER($column) || ' ') GLOB :t$index"

        /** Wraps a term as a whole-word `GLOB` pattern. */
        internal fun wordPattern(term: String): String = "*[^a-z]${escapeGlob(term.lowercase())}[^a-z]*"

        /**
         * Escapes the four characters `GLOB` treats as metacharacters by wrapping each in a bracket
         * expression, which matches it literally. `GLOB` has no `ESCAPE` clause, so this is the only
         * way; without it a query containing `*` would match every episode.
         */
        private fun escapeGlob(term: String): String = buildString {
            for (char in term) when (char) {
                '*', '?', '[' -> append('[').append(char).append(']')
                ']' -> append("[]]")
                else -> append(char)
            }
        }
    }
}
