package com.aisummarypodcast.store

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.PagingAndSortingRepository

interface EpisodePublicationRepository :
    CrudRepository<EpisodePublication, Long>,
    PagingAndSortingRepository<EpisodePublication, Long> {

    fun findByEpisodeId(episodeId: Long): List<EpisodePublication>

    fun findByEpisodeIdAndTarget(episodeId: Long, target: String): EpisodePublication?

    /**
     * Podcast-level paged listing. The service resolves the podcast's episode ids
     * first, then this derived query handles paging + sorting on the publication
     * row itself. No `@Query` needed because we never have to join `episodes`.
     */
    fun findByEpisodeIdIn(episodeIds: Collection<Long>, pageable: Pageable): Page<EpisodePublication>

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
