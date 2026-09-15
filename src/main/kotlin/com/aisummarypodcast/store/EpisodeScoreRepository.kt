package com.aisummarypodcast.store

import org.springframework.data.repository.CrudRepository

interface EpisodeScoreRepository : CrudRepository<EpisodeScore, Long> {

    fun findByEpisodeIdAndScorerVersion(episodeId: Long, scorerVersion: Int): EpisodeScore?

    fun findByEpisodeIdOrderByScorerVersionDesc(episodeId: Long): List<EpisodeScore>
}
