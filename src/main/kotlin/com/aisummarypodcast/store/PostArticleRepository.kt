package com.aisummarypodcast.store

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface PostArticleRepository : CrudRepository<PostArticle, Long>, PostArticleRepositoryCustom {

    fun findByArticleId(articleId: Long): List<PostArticle>

    fun deleteByArticleId(articleId: Long)

    fun countByArticleId(articleId: Long): Long

    @Query("SELECT COUNT(*) FROM post_articles WHERE article_id IN (:articleIds)")
    fun countByArticleIds(articleIds: List<Long>): Long
}
