package com.aisummarypodcast.store

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface EpisodePublicationRepository : CrudRepository<EpisodePublication, Long> {

    fun findByEpisodeId(episodeId: Long): List<EpisodePublication>

    fun findByEpisodeIdAndTarget(episodeId: Long, target: String): EpisodePublication?

    /**
     * A page of the podcast's publications, newest first by publication creation. Joins `episodes`
     * to scope to the podcast; Pageable is not supported on `@Query`, so the page bounds are
     * explicit and [countByPodcastId] supplies the total.
     */
    @Query("""
        SELECT ep.* FROM episode_publications ep
        JOIN episodes e ON ep.episode_id = e.id
        WHERE e.podcast_id = :podcastId
        ORDER BY ep.created_at DESC, ep.id DESC
        LIMIT :limit OFFSET :offset
    """)
    fun findPageByPodcastId(podcastId: String, limit: Int, offset: Long): List<EpisodePublication>

    @Query("""
        SELECT COUNT(*) FROM episode_publications ep
        JOIN episodes e ON ep.episode_id = e.id
        WHERE e.podcast_id = :podcastId
    """)
    fun countByPodcastId(podcastId: String): Long

    // Status values must match EpisodeStatus enum names.
    // Ordered newest-first by the episode's generatedAt so the caller (SoundCloud playlist
    // rebuild) gets DB-sorted rows and need not sort in memory.
    @Query("""
        SELECT ep.* FROM episode_publications ep
        JOIN episodes e ON ep.episode_id = e.id
        WHERE e.podcast_id = :podcastId AND ep.target = :target AND ep.status = 'PUBLISHED'
        ORDER BY e.generated_at DESC
    """)
    fun findPublishedByPodcastIdAndTarget(podcastId: String, target: String): List<EpisodePublication>
}
