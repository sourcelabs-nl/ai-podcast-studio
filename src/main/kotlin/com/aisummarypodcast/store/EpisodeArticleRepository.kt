package com.aisummarypodcast.store

import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface EpisodeArticleRepository : CrudRepository<EpisodeArticle, Long>, EpisodeArticleRepositoryCustom {

    fun findByEpisodeId(episodeId: Long): List<EpisodeArticle>

    @Modifying
    @Query("INSERT OR IGNORE INTO episode_articles (episode_id, article_id, topic, topic_order) VALUES (:episodeId, :articleId, :topic, :topicOrder)")
    fun insertIgnore(episodeId: Long, articleId: Long, topic: String? = null, topicOrder: Int? = null)

    /**
     * Demotes to "background" (clears topic_order) every link for the episode whose topic is NOT in
     * the supplied set of topics actually discussed in the script. The article link and its topic
     * label are retained. Callers MUST pass a non-empty [coveredTopics]; an empty set would clear
     * every topic_order (SQLite treats `NOT IN ()` as true), so guard against that at the call site.
     */
    @Modifying
    @Query("UPDATE episode_articles SET topic_order = NULL WHERE episode_id = :episodeId AND topic_order IS NOT NULL AND (topic IS NULL OR topic NOT IN (:coveredTopics))")
    fun clearTopicOrderForUncoveredTopics(episodeId: Long, coveredTopics: Collection<String>)

    // Status values must match EpisodeStatus enum names
    @Query("""
        SELECT COUNT(*) > 0 FROM episode_articles ea
        JOIN episodes e ON ea.episode_id = e.id
        JOIN episode_publications ep ON ep.episode_id = e.id
        WHERE ea.article_id = :articleId
          AND e.status = 'GENERATED'
          AND ep.status = 'PUBLISHED'
    """)
    fun isArticleLinkedToPublishedEpisode(articleId: Long): Boolean

}
