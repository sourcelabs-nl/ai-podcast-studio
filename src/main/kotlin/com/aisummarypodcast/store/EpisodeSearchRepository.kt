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
        val topicMatches = terms.indices.joinToString(" OR ") { "LOWER(COALESCE(ea.topic, '')) LIKE :t$it ESCAPE '\\'" }
        val articleMatches = terms.indices.joinToString(" OR ") { ARTICLE_TEXT_MATCH.replace("?", "$it") }

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
            .params(terms.mapIndexed { index, term -> "t$index" to likePattern(term) }.toMap())
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
            LOWER(e.script_text) LIKE :t$index ESCAPE '\'
            OR LOWER(COALESCE(e.recap, '')) LIKE :t$index ESCAPE '\'
            OR LOWER(COALESCE(e.show_notes, '')) LIKE :t$index ESCAPE '\'
            OR EXISTS (
                SELECT 1 FROM episode_articles ea
                JOIN articles a ON a.id = ea.article_id
                WHERE ea.episode_id = e.id
                  AND ea.topic_order IS NOT NULL
                  AND (LOWER(COALESCE(ea.topic, '')) LIKE :t$index ESCAPE '\' OR ${ARTICLE_TEXT_MATCH.replace("?", "$index")})
            )
        )
    """.trimIndent()

    private fun buildParams(
        podcastId: String,
        statuses: Collection<EpisodeStatus>,
        terms: List<String>
    ): Map<String, Any> = buildMap {
        put("podcastId", podcastId)
        if (statuses.isNotEmpty()) put("statuses", statuses.map { it.name })
        terms.forEachIndexed { index, term -> put("t$index", likePattern(term)) }
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
         * A covered article matches on its headline, its LLM-written summary, or its body. All three
         * are needed: an article's subject often appears only in the text, so matching the headline
         * alone reports "mentioned in the script only" for an episode whose source material plainly
         * discusses the term. `?` is a placeholder for the term index, filled in by the caller.
         */
        private const val ARTICLE_TEXT_MATCH =
            "LOWER(a.title) LIKE :t? ESCAPE '\\' " +
                "OR LOWER(COALESCE(a.summary, '')) LIKE :t? ESCAPE '\\' " +
                "OR LOWER(a.body) LIKE :t? ESCAPE '\\'"

        /**
         * Wraps a term for a substring match, escaping the characters `LIKE` would otherwise treat
         * as wildcards. Without this a query containing `%` would match every episode.
         */
        internal fun likePattern(term: String): String {
            val escaped = term.lowercase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            return "%$escaped%"
        }
    }
}
