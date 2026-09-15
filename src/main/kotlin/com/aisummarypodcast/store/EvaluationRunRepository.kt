package com.aisummarypodcast.store

import org.springframework.data.repository.CrudRepository

interface EvaluationRunRepository : CrudRepository<EvaluationRun, Long> {

    /**
     * The most recent runs for a podcast. Capped in the query rather than in the caller: an
     * ablation writes one row per repetition, so this table only grows, and an uncapped read would
     * eventually load every experiment ever run into memory and onto the response.
     */
    fun findTop200ByPodcastIdOrderByIdDesc(podcastId: String): List<EvaluationRun>

    /** Bounded by the number of times one episode was produced, which is small. */
    fun findByEpisodeId(episodeId: Long): List<EvaluationRun>
}
