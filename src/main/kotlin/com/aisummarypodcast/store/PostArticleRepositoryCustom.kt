package com.aisummarypodcast.store

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

interface PostArticleRepositoryCustom {
    /**
     * Links a post to an article, doing nothing when the link already exists.
     *
     * Aggregation runs from two paths that can overlap on the same posts: the eager scoring that
     * follows a poll, and the episode pipeline. Both derive the same article from the same post,
     * so a plain insert loses the race on `UNIQUE(post_id, article_id)` and fails the episode.
     */
    fun linkIfAbsent(postId: Long, articleId: Long)
}

@Repository
class PostArticleRepositoryCustomImpl(
    private val jdbcClient: JdbcClient
) : PostArticleRepositoryCustom {

    override fun linkIfAbsent(postId: Long, articleId: Long) {
        jdbcClient.sql("INSERT OR IGNORE INTO post_articles (post_id, article_id) VALUES (?, ?)")
            .params(postId, articleId)
            .update()
    }
}
