package com.aisummarypodcast.store

import com.aisummarypodcast.podcast.ArticleSourceResponse
import com.aisummarypodcast.podcast.EpisodeArticleResponse
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

data class FeedArticle(val title: String, val url: String, val topic: String? = null, val topicOrder: Int? = null)

data class TopicGroupedArticle(val title: String, val url: String, val topic: String?, val topicOrder: Int?)

interface EpisodeArticleRepositoryCustom {
    fun findArticlesWithSourcesByEpisodeId(episodeId: Long): List<EpisodeArticleResponse>
    fun findArticlesByEpisodeIds(episodeIds: List<Long>): Map<Long, List<FeedArticle>>
    fun findRawArticlesByEpisodeId(episodeId: Long): List<Article>
    fun findArticlesWithTopicsByEpisodeId(episodeId: Long): List<TopicGroupedArticle>
}

@Repository
class EpisodeArticleRepositoryCustomImpl(
    private val jdbcClient: JdbcClient
) : EpisodeArticleRepositoryCustom {

    override fun findArticlesWithSourcesByEpisodeId(episodeId: Long): List<EpisodeArticleResponse> {
        return jdbcClient.sql(
            """
            SELECT a.id, a.title, a.url, a.author, a.published_at, a.relevance_score, a.summary, a.body, a.subtopic,
                   s.id AS source_id, s.type AS source_type, s.url AS source_url, s.label AS source_label,
                   (SELECT COUNT(*) FROM post_articles pa WHERE pa.article_id = a.id) AS post_count
            FROM episode_articles ea
            JOIN articles a ON ea.article_id = a.id
            JOIN sources s ON a.source_id = s.id
            WHERE ea.episode_id = :episodeId
            ORDER BY a.relevance_score DESC NULLS LAST
            """.trimIndent()
        )
            .param("episodeId", episodeId)
            .query { rs, _ ->
                EpisodeArticleResponse(
                    id = rs.getLong("id"),
                    title = rs.getString("title"),
                    url = rs.getString("url"),
                    author = rs.getString("author"),
                    publishedAt = rs.getString("published_at"),
                    relevanceScore = rs.getObject("relevance_score") as? Int,
                    summary = rs.getString("summary"),
                    body = rs.getString("body"),
                    subtopic = rs.getString("subtopic"),
                    // An article with no post links was not aggregated from posts; it counts as one.
                    postCount = rs.getInt("post_count").coerceAtLeast(1),
                    source = ArticleSourceResponse(
                        id = rs.getString("source_id"),
                        type = rs.getString("source_type"),
                        url = rs.getString("source_url"),
                        label = rs.getString("source_label")
                    )
                )
            }
            .list()
    }

    override fun findRawArticlesByEpisodeId(episodeId: Long): List<Article> {
        return jdbcClient.sql(
            """
            SELECT a.*
            FROM episode_articles ea
            JOIN articles a ON ea.article_id = a.id
            WHERE ea.episode_id = :episodeId
            ORDER BY a.relevance_score DESC NULLS LAST
            """.trimIndent()
        )
            .param("episodeId", episodeId)
            .query { rs, _ ->
                Article(
                    id = rs.getLong("id"),
                    sourceId = rs.getString("source_id"),
                    title = rs.getString("title"),
                    body = rs.getString("body"),
                    url = rs.getString("url"),
                    publishedAt = rs.getString("published_at"),
                    author = rs.getString("author"),
                    contentHash = rs.getString("content_hash"),
                    relevanceScore = rs.getObject("relevance_score") as? Int,
                    isProcessed = (rs.getObject("is_processed") as? Number)?.toInt() != 0,
                    summary = rs.getString("summary"),
                    llmInputTokens = rs.getObject("llm_input_tokens") as? Int,
                    llmOutputTokens = rs.getObject("llm_output_tokens") as? Int,
                    llmCostCents = rs.getObject("llm_cost_cents") as? Int,
                    llmReportedCostUsd = (rs.getObject("llm_reported_cost_usd") as? Number)?.toDouble()
                )
            }
            .list()
    }

    override fun findArticlesWithTopicsByEpisodeId(episodeId: Long): List<TopicGroupedArticle> {
        return jdbcClient.sql(
            """
            SELECT a.title, a.url, ea.topic, ea.topic_order
            FROM episode_articles ea
            JOIN articles a ON ea.article_id = a.id
            WHERE ea.episode_id = :episodeId
            ORDER BY ea.topic_order ASC NULLS LAST, a.relevance_score DESC NULLS LAST
            """.trimIndent()
        )
            .param("episodeId", episodeId)
            .query { rs, _ ->
                TopicGroupedArticle(
                    title = rs.getString("title"),
                    url = rs.getString("url"),
                    topic = rs.getString("topic"),
                    topicOrder = rs.getObject("topic_order") as? Int
                )
            }
            .list()
    }

    override fun findArticlesByEpisodeIds(episodeIds: List<Long>): Map<Long, List<FeedArticle>> {
        if (episodeIds.isEmpty()) return emptyMap()
        val placeholders = episodeIds.indices.joinToString(",") { ":id$it" }
        val params = mutableMapOf<String, Any>()
        episodeIds.forEachIndexed { i, id -> params["id$i"] = id }

        data class Row(val episodeId: Long, val title: String, val url: String, val topic: String?, val topicOrder: Int?)

        val rows = jdbcClient.sql(
            """
            SELECT ea.episode_id, a.title, a.url, ea.topic, ea.topic_order
            FROM episode_articles ea
            JOIN articles a ON ea.article_id = a.id
            WHERE ea.episode_id IN ($placeholders)
            ORDER BY ea.topic_order ASC NULLS LAST, a.relevance_score DESC NULLS LAST
            """.trimIndent()
        )
            .params(params)
            .query { rs, _ ->
                val topicOrder = rs.getInt("topic_order").let { if (rs.wasNull()) null else it }
                Row(rs.getLong("episode_id"), rs.getString("title"), rs.getString("url"), rs.getString("topic"), topicOrder)
            }
            .list()

        return rows.groupBy({ it.episodeId }, { FeedArticle(it.title, it.url, it.topic, it.topicOrder) })
    }
}
