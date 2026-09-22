package com.aisummarypodcast.store

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.PagingAndSortingRepository

interface EpisodeRepository : CrudRepository<Episode, Long>, PagingAndSortingRepository<Episode, Long> {

    fun findByPodcastIdOrderByGeneratedAtDescIdDesc(podcastId: String): List<Episode>

    fun findByPodcastIdAndStatusOrderByGeneratedAtDescIdDesc(podcastId: String, status: EpisodeStatus): List<Episode>

    fun findByPodcastId(podcastId: String, pageable: Pageable): Page<Episode>

    fun findByPodcastIdAndStatusIn(podcastId: String, statuses: Collection<EpisodeStatus>, pageable: Pageable): Page<Episode>

    fun findByPodcastId(podcastId: String): List<Episode>

    fun findByPodcastIdAndStatus(podcastId: String, status: EpisodeStatus): List<Episode>

    fun findByPodcastIdAndStatusIn(podcastId: String, statuses: Collection<EpisodeStatus>): List<Episode>

    /**
     * The podcast's most recent regular episode that still covers its window: neither failed nor
     * discarded, published or not. Discard rolls the schedule back to it, so a slot an unpublished
     * episode already covered is not treated as due again.
     */
    // Status values must match EpisodeStatus enum names
    @Query("""
        SELECT * FROM episodes
        WHERE podcast_id = :podcastId
          AND window_end IS NOT NULL
          AND status NOT IN ('FAILED', 'DISCARDED')
          AND focus IS NULL
        ORDER BY generated_at DESC
        LIMIT 1
    """)
    fun findLatestCoveringByPodcastId(podcastId: String): Episode?

    /**
     * The podcast's most recent regular episodes. A focus episode is left out: it never consumed its
     * articles, so the dedup stage must not treat them as already covered.
     */
    // Status values must match EpisodeStatus enum names
    @Query("SELECT * FROM episodes WHERE podcast_id = :podcastId AND status = 'GENERATED' AND focus IS NULL ORDER BY generated_at DESC LIMIT :limit")
    fun findRecentGeneratedByPodcastId(podcastId: String, limit: Int): List<Episode>

    fun findByStatus(status: EpisodeStatus): List<Episode>

    /**
     * Where the podcast's article coverage ends at or before [windowEnd]: the latest `window_end` of
     * an episode that carries a window and was neither failed nor discarded. A failed or discarded
     * episode covered nothing, so its window must stay claimable by a later run. A focus episode is
     * left out for the same reason: it does not advance the regular schedule.
     */
    // Status values must match EpisodeStatus enum names
    @Query("""
        SELECT window_end FROM episodes
        WHERE podcast_id = :podcastId
          AND window_end IS NOT NULL
          AND window_end <= :windowEnd
          AND status NOT IN ('FAILED', 'DISCARDED')
          AND focus IS NULL
        ORDER BY window_end DESC
        LIMIT 1
    """)
    fun findLatestCoveredWindowEnd(podcastId: String, windowEnd: String): String?
}
