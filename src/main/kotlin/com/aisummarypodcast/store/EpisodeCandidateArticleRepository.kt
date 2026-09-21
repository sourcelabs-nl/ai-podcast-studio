package com.aisummarypodcast.store

import org.springframework.data.repository.CrudRepository

interface EpisodeCandidateArticleRepository : CrudRepository<EpisodeCandidateArticle, Long> {

    fun findByEpisodeId(episodeId: Long): List<EpisodeCandidateArticle>

    fun deleteByEpisodeId(episodeId: Long)
}
