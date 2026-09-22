package com.aisummarypodcast.store

import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface EpisodeCandidateArticleRepository : CrudRepository<EpisodeCandidateArticle, Long> {

    fun findByEpisodeId(episodeId: Long): List<EpisodeCandidateArticle>

    fun deleteByEpisodeId(episodeId: Long)

    /**
     * Written row by row rather than through `saveAll`. Spring Data JDBC batches a `saveAll` of new
     * aggregates into one `executeBatch`, then reads the generated keys back to populate each id;
     * the SQLite driver inserts the rows but returns no keys from a batch, so every save failed with
     * "After saving the identifier must not be null". This insert never asks for the key.
     *
     * OR IGNORE holds the same line as [EpisodeArticleRepository.insertIgnore]: a candidate recorded
     * twice for one episode is the same fact stated twice, not a reason to fail the episode.
     */
    @Modifying
    @Query("INSERT OR IGNORE INTO episode_candidate_articles (episode_id, article_id, outcome) VALUES (:episodeId, :articleId, :outcome)")
    fun insertIgnore(episodeId: Long, articleId: Long, outcome: String)
}
