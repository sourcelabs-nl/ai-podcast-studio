package com.aisummarypodcast.store

import org.springframework.data.repository.CrudRepository

interface EpisodeResearchSourceRepository : CrudRepository<EpisodeResearchSource, Long> {

    fun findByEpisodeIdOrderByOrdinal(episodeId: Long): List<EpisodeResearchSource>

    fun deleteByEpisodeId(episodeId: Long)
}
