package com.aisummarypodcast.eval

import com.aisummarypodcast.podcast.EpisodeService
import com.aisummarypodcast.store.Episode
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

/**
 * Computes [ScriptMetrics] over stored episode scripts.
 *
 * Nothing is persisted. The computation is free and always agrees with the script it describes,
 * whereas a stored copy would need a version of its own and could silently disagree after a script
 * is edited. The judged score is stored precisely because it lacks both of those properties.
 */
@Service
class ScriptMetricsService(private val episodeService: EpisodeService) {

    fun forEpisode(episode: Episode): EpisodeScriptMetrics = episode.toMetrics()

    /**
     * The most recent [limit] episodes of a podcast, newest first, so the archive can be read as a
     * distribution rather than one episode at a time.
     */
    fun forPodcast(podcastId: String, limit: Int): List<EpisodeScriptMetrics> {
        val pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "generatedAt", "id"))
        return episodeService.findByPodcastIdPaged(podcastId, emptyList(), pageable)
            .content
            .map { it.toMetrics() }
    }

    private fun Episode.toMetrics() = EpisodeScriptMetrics(
        episodeId = requireNotNull(id) { "Episode read from the store has no id" },
        generatedAt = generatedAt,
        durationSeconds = durationSeconds,
        metrics = ScriptMetrics.of(scriptText.orEmpty())
    )
}
