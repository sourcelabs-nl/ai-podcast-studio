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

    @Query("SELECT * FROM episodes WHERE podcast_id = :podcastId AND status IN (:statuses)")
    fun findByPodcastIdAndStatusIn(podcastId: String, statuses: List<String>): List<Episode>

    // Status values must match EpisodeStatus enum names
    @Query("""
        SELECT e.* FROM episodes e
        JOIN episode_publications ep ON ep.episode_id = e.id
        WHERE e.podcast_id = :podcastId
          AND e.status = 'GENERATED'
          AND ep.status = 'PUBLISHED'
        ORDER BY e.generated_at DESC
        LIMIT 1
    """)
    fun findLatestPublishedByPodcastId(podcastId: String): Episode?

    // Status values must match EpisodeStatus enum names
    @Query("SELECT * FROM episodes WHERE podcast_id = :podcastId AND status = 'GENERATED' ORDER BY generated_at DESC LIMIT :limit")
    fun findRecentGeneratedByPodcastId(podcastId: String, limit: Int): List<Episode>

    fun findByStatus(status: EpisodeStatus): List<Episode>
}
