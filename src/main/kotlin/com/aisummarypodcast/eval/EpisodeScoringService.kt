package com.aisummarypodcast.eval

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.JudgeMode
import com.aisummarypodcast.llm.ModelResolver
import com.aisummarypodcast.llm.PipelineStage
import com.aisummarypodcast.podcast.EpisodeService
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeScore
import com.aisummarypodcast.store.EpisodeScoreRepository
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastRepository
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

/**
 * Judges episode scripts and stores the result.
 *
 * This is where the three modes live. The judge itself has no mode: it is asked the same question
 * and returns the same anchors whichever mode is configured, so a score written under
 * [JudgeMode.ADVISE] and one written under [JudgeMode.ENFORCE] remain comparable.
 */
@Service
class EpisodeScoringService(
    private val scriptJudge: ScriptJudge,
    private val episodeService: EpisodeService,
    private val podcastRepository: PodcastRepository,
    private val episodeScoreRepository: EpisodeScoreRepository,
    private val modelResolver: ModelResolver,
    private val jsonMapper: JsonMapper,
    private val appProperties: AppProperties
) {

    companion object {
        /**
         * Bumped whenever the judge prompt or the derived arithmetic changes, because either makes
         * an older row describe a different quantity. Rows at different versions are reported
         * separately, never averaged.
         */
        const val SCORER_VERSION = 1
    }

    private val log = LoggerFactory.getLogger(javaClass)

    private val judgeProperties get() = appProperties.eval.judge

    /**
     * Scores the most recent [limit] episodes of a podcast, skipping any already scored at the
     * current version so re-running over the archive costs nothing for work already done.
     *
     * Returns an empty list in [JudgeMode.OFF] without reaching the model.
     */
    suspend fun scorePodcast(podcastId: String, limit: Int): List<ScoringOutcome> {
        if (judgeProperties.mode == JudgeMode.OFF) {
            log.info("[EVAL] Judge is OFF; not scoring podcast {}", podcastId)
            return emptyList()
        }
        warnIfEnforcingWithoutNorm()
        val pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "generatedAt", "id"))
        val episodes = episodeService.findByPodcastIdPaged(podcastId, emptyList(), pageable).content
        return episodes.mapNotNull { scoreIfNeeded(it) }
    }

    /**
     * Scores one episode, or returns its existing score at the current version without a model
     * call. Null in [JudgeMode.OFF], and null for a script with no speaker turns.
     */
    suspend fun scoreEpisode(episode: Episode): ScoringOutcome? {
        if (judgeProperties.mode == JudgeMode.OFF) return null
        warnIfEnforcingWithoutNorm()
        return scoreIfNeeded(episode)
    }

    fun existingScores(episodeId: Long): List<EpisodeScore> =
        episodeScoreRepository.findByEpisodeIdOrderByScorerVersionDesc(episodeId)

    private suspend fun scoreIfNeeded(episode: Episode): ScoringOutcome? {
        val episodeId = requireNotNull(episode.id) { "Episode read from the store has no id" }
        episodeScoreRepository.findByEpisodeIdAndScorerVersion(episodeId, SCORER_VERSION)?.let {
            return ScoringOutcome(it, decide(it.overall))
        }
        if (episode.scriptText.isBlank()) {
            log.warn("[EVAL] Episode {} has no script to judge", episodeId)
            return null
        }
        // The repository rather than PodcastService: the generation pipeline calls this service, so
        // depending on PodcastService here would close a bean cycle.
        val podcast = podcastRepository.findByIdOrNull(episode.podcastId) ?: run {
            log.warn("[EVAL] Episode {} refers to podcast {}, which no longer exists", episodeId, episode.podcastId)
            return null
        }
        return judgeAndStore(episodeId, episode, podcast)
    }

    private suspend fun judgeAndStore(episodeId: Long, episode: Episode, podcast: Podcast): ScoringOutcome? {
        val evalModel = modelResolver.resolve(podcast, PipelineStage.EVAL)
        // A script the judge cannot answer for is skipped, not fatal. Scoring runs over the whole
        // archive at once, and letting one unreadable script abort the run would throw away every
        // episode after it along with the calls already paid for.
        val judgement = try {
            scriptJudge.judge(episode.scriptText, podcast.userId, evalModel, episode.id)
        } catch (e: CancellationException) {
            // Cancellation is the caller unwinding, not a script the judge could not read.
            throw e
        } catch (e: Exception) {
            log.warn("[EVAL] Episode {} could not be judged: {}", episodeId, e.message)
            return null
        }
        val score = AttentionScoring.of(judgement.anchors)
        val stored = episodeScoreRepository.save(
            EpisodeScore(
                episodeId = episodeId,
                scorerVersion = SCORER_VERSION,
                judgeModel = evalModel.model,
                scoredAt = Instant.now().toString(),
                overall = score.overall,
                cliffhangerScore = score.cliffhangers.score,
                humorScore = score.humor.score,
                teaserScore = score.teaser.score,
                promises = score.cliffhangers.promises,
                deferredPromises = score.cliffhangers.deferredPromises,
                unpaidPromises = score.cliffhangers.unpaidPromises,
                medianDeferralTurns = score.cliffhangers.medianDeferralTurns,
                humorBeats = score.humor.beats,
                humorSpeakerBalance = score.humor.speakerBalance,
                humorReactionRatio = score.humor.reactionRatio,
                teaserTopics = score.teaser.distinctTopics,
                anchorsJson = jsonMapper.writeValueAsString(judgement.anchors),
                inputTokens = judgement.usage.inputTokens,
                outputTokens = judgement.usage.outputTokens,
                costCents = judgement.costCents
            )
        )
        val decision = decide(score.overall)
        log.info("[EVAL] Episode {} scored {} ({})", episodeId, "%.2f".format(score.overall), decision.reason)
        return ScoringOutcome(stored, decision)
    }

    /**
     * Applies the configured mode to a score.
     *
     * [JudgeMode.ENFORCE] without a norm resolves to the same decision [JudgeMode.ADVISE] would
     * reach. The mode is still reported as it was configured, so the record distinguishes a run
     * that chose not to enforce from one that enforced and found nothing wrong.
     */
    internal fun decide(overall: Double): JudgeDecision {
        val mode = judgeProperties.mode
        val norm = judgeProperties.norm
        return when {
            mode != JudgeMode.ENFORCE ->
                JudgeDecision(mode, norm, belowNorm = false, reason = "advisory only")
            norm == null ->
                JudgeDecision(mode, null, belowNorm = false, reason = "no norm configured, advising only")
            overall < norm ->
                JudgeDecision(mode, norm, belowNorm = true, reason = "below the norm of ${"%.2f".format(norm)}")
            else ->
                JudgeDecision(mode, norm, belowNorm = false, reason = "at or above the norm of ${"%.2f".format(norm)}")
        }
    }

    private fun warnIfEnforcingWithoutNorm() {
        if (judgeProperties.mode == JudgeMode.ENFORCE && judgeProperties.norm == null) {
            log.warn(
                "[EVAL] Judge mode is ENFORCE but app.eval.judge.norm is unset, so this run advises " +
                    "only. Set a norm once a baseline of judged scores has been read."
            )
        }
    }
}
