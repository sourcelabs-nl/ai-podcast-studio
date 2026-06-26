package com.aisummarypodcast.store

import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface ArticleRepository : CrudRepository<Article, Long>, ArticleRepositoryCustom {

    @Query("SELECT * FROM articles WHERE relevance_score IS NULL AND source_id IN (:sourceIds)")
    fun findUnscoredBySourceIds(sourceIds: List<String>): List<Article>

    @Query("SELECT * FROM articles WHERE relevance_score >= :threshold AND is_processed = 0 AND source_id IN (:sourceIds)")
    fun findRelevantUnprocessedBySourceIds(sourceIds: List<String>, threshold: Int): List<Article>

    @Query("SELECT * FROM articles WHERE source_id IN (:sourceIds) AND published_at >= :since")
    fun findAllSince(sourceIds: List<String>, since: String): List<Article>

    @Query("SELECT * FROM articles WHERE source_id IN (:sourceIds) AND is_processed = 0 AND published_at >= :since ORDER BY relevance_score DESC NULLS LAST")
    fun findUnprocessedSince(sourceIds: List<String>, since: String): List<Article>

    fun findBySourceIdAndContentHash(sourceId: String, contentHash: String): Article?

    fun deleteBySourceId(sourceId: String)

    @Modifying
    @Query("DELETE FROM articles WHERE published_at IS NOT NULL AND published_at < :cutoff AND is_processed = 0")
    fun deleteOldUnprocessedArticles(cutoff: String)
}
