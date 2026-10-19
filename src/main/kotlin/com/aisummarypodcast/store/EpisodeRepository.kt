package com.aisummarypodcast.store

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.PagingAndSortingRepository

interface EpisodeRepository : CrudRepository<Episode, Long>, PagingAndSortingRepository<Episode, Long> {

    /*
     * The methods taking a `purpose` to exclude serve the listings, the feed and the active-episode
     * check, which callers pass EpisodePurpose.EXPERIMENT to.
     */
    fun findByPodcastIdAndPurposeNotOrderByGeneratedAtDescIdDesc(podcastId: String, purpose: EpisodePurpose): List<Episode>

    fun findByPodcastIdAndStatusAndPurposeNotOrderByGeneratedAtDescIdDesc(
        podcastId: String,
        status: EpisodeStatus,
        purpose: EpisodePurpose
    ): List<Episode>

    fun findByPodcastId(podcastId: String, pageable: Pageable): Page<Episode>

    fun findByPodcastIdAndPurposeNot(podcastId: String, purpose: EpisodePurpose, pageable: Pageable): Page<Episode>

    fun findByPodcastIdAndStatusInAndPurposeNot(
        podcastId: String,
        statuses: Collection<EpisodeStatus>,
        purpose: EpisodePurpose,
        pageable: Pageable
    ): Page<Episode>

    fun findByPodcastId(podcastId: String): List<Episode>

    fun findByPodcastIdAndGeneratedAtLessThan(podcastId: String, cutoff: String): List<Episode>

    /** Whether a regular (non-focus) episode of the podcast has one of [statuses], with episodes of [purpose] left out. */
    fun existsByPodcastIdAndStatusInAndPurposeNotAndFocusIsNull(
        podcastId: String,
        statuses: Collection<EpisodeStatus>,
        purpose: EpisodePurpose
    ): Boolean

    /** Whether a focus episode of the podcast has one of [statuses], with episodes of [purpose] left out. */
    fun existsByPodcastIdAndStatusInAndPurposeNotAndFocusIsNotNull(
        podcastId: String,
        statuses: Collection<EpisodeStatus>,
        purpose: EpisodePurpose
    ): Boolean

    /** The podcast's focus episodes in [status], oldest first, with episodes of [purpose] left out. */
    fun findByPodcastIdAndStatusAndPurposeNotAndFocusIsNotNullOrderByGeneratedAtAsc(
        podcastId: String,
        status: EpisodeStatus,
        purpose: EpisodePurpose
    ): List<Episode>

    /** As above, limited to the focus episodes generated strictly after [generatedAfter]. */
    fun findByPodcastIdAndStatusAndPurposeNotAndFocusIsNotNullAndGeneratedAtGreaterThanOrderByGeneratedAtAsc(
        podcastId: String,
        status: EpisodeStatus,
        purpose: EpisodePurpose,
        generatedAfter: String
    ): List<Episode>

    /** The experiment episodes recomposing [sourceEpisodeId]'s article set, in creation order. */
    fun findByExperimentSourceEpisodeIdOrderByIdAsc(sourceEpisodeId: Long): List<Episode>

    fun findByPodcastIdAndStatus(podcastId: String, status: EpisodeStatus): List<Episode>

    /** The newest episode of the podcast in [status] that has recorded a pipeline stage. */
    fun findFirstByPodcastIdAndStatusAndPipelineStageIsNotNullOrderByGeneratedAtDescIdDesc(
        podcastId: String,
        status: EpisodeStatus
    ): Episode?

    fun findByPodcastIdAndStatusInAndPurposeNot(
        podcastId: String,
        statuses: Collection<EpisodeStatus>,
        purpose: EpisodePurpose
    ): List<Episode>

    /**
     * The regular episodes for the slot ending at [windowEnd] that failed. The scheduler retries a
     * failed slot once, so it needs how many there are and when the latest one failed.
     */
    // Status and purpose values must match EpisodeStatus and EpisodePurpose enum names
    @Query("""
        SELECT * FROM episodes
        WHERE podcast_id = :podcastId
          AND window_end = :windowEnd
          AND status = 'FAILED'
          AND focus IS NULL
          AND purpose != 'EXPERIMENT'
    """)
    fun findFailedByPodcastIdAndWindowEnd(podcastId: String, windowEnd: String): List<Episode>

    /**
     * The podcast's most recent regular episodes. A focus episode is left out: it never consumed its
     * articles, so the dedup stage must not treat them as already covered. An experiment episode is
     * left out for the same reason.
     */
    // Status and purpose values must match EpisodeStatus and EpisodePurpose enum names
    @Query("SELECT * FROM episodes WHERE podcast_id = :podcastId AND status = 'GENERATED' AND focus IS NULL AND purpose != 'EXPERIMENT' ORDER BY generated_at DESC LIMIT :limit")
    fun findRecentGeneratedByPodcastId(podcastId: String, limit: Int): List<Episode>

    fun findByStatus(status: EpisodeStatus): List<Episode>

    /**
     * Where the podcast's article coverage ends at or before [windowEnd]: the latest `window_end` of
     * an episode that carries a window and was neither failed nor discarded. This is also the last
     * slot the podcast has served, which the scheduler counts its next slot from. A failed or discarded
     * episode covered nothing, so its window must stay claimable by a later run. A focus episode is
     * left out for the same reason: it does not advance the regular schedule. Neither does an
     * experiment episode.
     */
    // Status and purpose values must match EpisodeStatus and EpisodePurpose enum names
    @Query("""
        SELECT window_end FROM episodes
        WHERE podcast_id = :podcastId
          AND window_end IS NOT NULL
          AND window_end <= :windowEnd
          AND status NOT IN ('FAILED', 'DISCARDED')
          AND focus IS NULL
          AND purpose != 'EXPERIMENT'
        ORDER BY window_end DESC
        LIMIT 1
    """)
    fun findLatestCoveredWindowEnd(podcastId: String, windowEnd: String): String?
}
