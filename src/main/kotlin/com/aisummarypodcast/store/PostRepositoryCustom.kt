package com.aisummarypodcast.store

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

interface PostRepositoryCustom {
    fun getPostCountsBySourceIds(sourceIds: List<String>): Map<String, Int>

    /** Post counts keyed by article id. Articles with no post links are absent from the map. */
    fun getPostCountsByArticleIds(articleIds: List<Long>): Map<Long, Int>

    /** The posts an article was aggregated from, oldest first so a thread reads in written order. */
    fun findPostsByArticleId(articleId: Long): List<Post>
}

// Positional parameters required for dynamic IN clause (Spring Data @Query does not support dynamic list sizes)
@Repository
class PostRepositoryCustomImpl(
    private val jdbcClient: JdbcClient
) : PostRepositoryCustom {

    override fun getPostCountsBySourceIds(sourceIds: List<String>): Map<String, Int> {
        if (sourceIds.isEmpty()) return emptyMap()
        val placeholders = sourceIds.joinToString(",") { "?" }
        val sql = """
            SELECT source_id, COUNT(*) as total
            FROM posts
            WHERE source_id IN ($placeholders)
            GROUP BY source_id
        """.trimIndent()
        return jdbcClient.sql(sql)
            .params(*sourceIds.toTypedArray())
            .query { rs, _ ->
                rs.getString("source_id") to rs.getInt("total")
            }
            .list()
            .toMap()
    }

    override fun getPostCountsByArticleIds(articleIds: List<Long>): Map<Long, Int> {
        if (articleIds.isEmpty()) return emptyMap()
        val placeholders = articleIds.joinToString(",") { "?" }
        val sql = """
            SELECT article_id, COUNT(*) as total
            FROM post_articles
            WHERE article_id IN ($placeholders)
            GROUP BY article_id
        """.trimIndent()
        return jdbcClient.sql(sql)
            .params(*articleIds.toTypedArray())
            .query { rs, _ -> rs.getLong("article_id") to rs.getInt("total") }
            .list()
            .toMap()
    }

    override fun findPostsByArticleId(articleId: Long): List<Post> {
        val sql = """
            SELECT p.*
            FROM post_articles pa
            JOIN posts p ON p.id = pa.post_id
            WHERE pa.article_id = ?
            ORDER BY p.published_at ASC NULLS LAST, p.id ASC
        """.trimIndent()
        return jdbcClient.sql(sql)
            .param(articleId)
            .query { rs, _ ->
                Post(
                    id = rs.getLong("id"),
                    sourceId = rs.getString("source_id"),
                    title = rs.getString("title"),
                    body = rs.getString("body"),
                    url = rs.getString("url"),
                    publishedAt = rs.getString("published_at"),
                    author = rs.getString("author"),
                    contentHash = rs.getString("content_hash"),
                    createdAt = rs.getString("created_at")
                )
            }
            .list()
    }
}
