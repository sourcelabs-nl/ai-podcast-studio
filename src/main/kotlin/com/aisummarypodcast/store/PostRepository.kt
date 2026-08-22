package com.aisummarypodcast.store

import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface PostRepository : CrudRepository<Post, Long>, PostRepositoryCustom {

    fun findBySourceIdAndContentHash(sourceId: String, contentHash: String): Post?

    @Query("SELECT * FROM posts WHERE content_hash = :contentHash AND source_id IN (:sourceIds) LIMIT 1")
    fun findByContentHashAndSourceIdIn(contentHash: String, sourceIds: List<String>): Post?

    /**
     * Age is measured on `published_at` (falling back to `created_at` for posts a feed gave no
     * date for), matching how [ArticleRepository.deleteOldUnprocessedArticles] measures article
     * age. Filtering on `created_at` instead lets a post that a feed back-dated well past the
     * retention window stay eligible forever: aggregation turns it into an article, article
     * cleanup deletes that article for being too old, `ON DELETE CASCADE` drops the
     * `post_articles` link, and the next poll re-aggregates and re-scores the same post.
     */
    @Query("""
        SELECT p.* FROM posts p
        LEFT JOIN post_articles pa ON p.id = pa.post_id
        WHERE pa.id IS NULL
          AND p.source_id IN (:sourceIds)
          AND COALESCE(p.published_at, p.created_at) >= :cutoff
        ORDER BY p.created_at ASC
    """)
    fun findUnlinkedBySourceIds(sourceIds: List<String>, cutoff: String): List<Post>

    @Query("""
        SELECT p.* FROM posts p
        LEFT JOIN post_articles pa ON p.id = pa.post_id
        WHERE pa.id IS NULL
          AND p.source_id IN (:sourceIds)
          AND COALESCE(p.published_at, p.created_at) >= :since
        ORDER BY p.created_at ASC
    """)
    fun findUnlinkedSince(sourceIds: List<String>, since: String): List<Post>

    @Modifying
    @Query("""
        DELETE FROM posts
        WHERE COALESCE(published_at, created_at) < :cutoff
          AND id NOT IN (SELECT post_id FROM post_articles)
    """)
    fun deleteOldUnlinkedPosts(cutoff: String)

    fun deleteBySourceId(sourceId: String)
}
