package com.aisummarypodcast.store

import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface ArticleRepository : CrudRepository<Article, Long>, ArticleRepositoryCustom {

    fun findByRelevanceScoreIsNullAndSourceIdIn(sourceIds: Collection<String>): List<Article>

    @Query("SELECT * FROM articles WHERE relevance_score >= :threshold AND is_processed = 0 AND source_id IN (:sourceIds)")
    fun findRelevantUnprocessedBySourceIds(sourceIds: List<String>, threshold: Int): List<Article>

    fun findBySourceIdInAndPublishedAtGreaterThanEqual(sourceIds: Collection<String>, since: String): List<Article>

    @Query("SELECT * FROM articles WHERE source_id IN (:sourceIds) AND is_processed = 0 AND published_at >= :since ORDER BY relevance_score DESC NULLS LAST")
    fun findUnprocessedSince(sourceIds: List<String>, since: String): List<Article>

    fun findBySourceIdAndContentHash(sourceId: String, contentHash: String): Article?

    /**
     * Stores an aggregated article unless one with the same `(source_id, content_hash)` exists. The
     * conflict is resolved inside SQLite rather than by catching an exception: an exception that
     * passes through a repository call marks the surrounding transaction rollback-only, so the
     * caller could not recover from it and its commit would fail.
     */
    @Modifying
    @Query("INSERT OR IGNORE INTO articles (source_id, title, body, url, published_at, author, content_hash) VALUES (:sourceId, :title, :body, :url, :publishedAt, :author, :contentHash)")
    fun insertIgnore(
        sourceId: String,
        title: String,
        body: String,
        url: String,
        publishedAt: String?,
        author: String?,
        contentHash: String
    )

    fun deleteBySourceId(sourceId: String)

    @Modifying
    @Query("DELETE FROM articles WHERE published_at IS NOT NULL AND published_at < :cutoff AND is_processed = 0")
    fun deleteOldUnprocessedArticles(cutoff: String)
}
