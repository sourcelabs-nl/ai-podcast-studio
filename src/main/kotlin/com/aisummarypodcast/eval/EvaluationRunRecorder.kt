package com.aisummarypodcast.eval

import com.aisummarypodcast.llm.EvaluationRunProvenance
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EvaluationRun
import com.aisummarypodcast.store.EvaluationRunRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

/**
 * Records what an evaluation run was composed under, once the episode it produced exists.
 *
 * The record is written here rather than in the composer because the episode has no id until after
 * composition, and a run is only interpretable together with the script it produced.
 */
@Service
class EvaluationRunRecorder(
    private val evaluationRunRepository: EvaluationRunRepository,
    private val jsonMapper: JsonMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Writes the run for [episode], or does nothing when [provenance] is null, which is every
     * ordinary generation.
     *
     * A failure here is logged and swallowed: losing the record of an experiment must not fail the
     * episode that experiment produced, which is a deliverable in its own right.
     */
    fun record(episode: Episode, provenance: EvaluationRunProvenance?): EvaluationRun? {
        if (provenance == null) return null
        val episodeId = episode.id ?: return null
        return try {
            val saved = evaluationRunRepository.save(
                EvaluationRun(
                    episodeId = episodeId,
                    podcastId = episode.podcastId,
                    ranAt = Instant.now().toString(),
                    promptHash = provenance.promptHash,
                    varietySelection = provenance.varietySelection,
                    composeModel = provenance.composeModel,
                    temperature = provenance.temperature,
                    cacheBypassed = provenance.cacheBypassed,
                    cacheHit = provenance.cacheHit,
                    toolsFiredJson = jsonMapper.writeValueAsString(provenance.toolsFired)
                )
            )
            log.info("[EVAL] Recorded evaluation run for episode {} (prompt {}, model {}, temperature {})",
                episodeId, provenance.promptHash.take(12), provenance.composeModel, provenance.temperature)
            saved
        } catch (e: Exception) {
            log.warn("[EVAL] Could not record the evaluation run for episode {}: {}", episodeId, e.message)
            null
        }
    }

    /** The most recent runs for a podcast, newest first and capped in the query. */
    fun runsForPodcast(podcastId: String): List<EvaluationRun> =
        evaluationRunRepository.findTop200ByPodcastIdOrderByIdDesc(podcastId)

    fun runsForEpisode(episodeId: Long): List<EvaluationRun> =
        evaluationRunRepository.findByEpisodeId(episodeId)
}
