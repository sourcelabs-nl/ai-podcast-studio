package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.eval.EpisodeScoringService
import com.aisummarypodcast.eval.ScriptMetrics
import com.aisummarypodcast.store.CostStage
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodePurpose
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.LlmCallRepository
import com.aisummarypodcast.store.LlmCallRow
import com.aisummarypodcast.store.Podcast
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Experiments: an existing episode's article set recomposed under several configuration variants,
 * each several times, to compare them. Every run is an ordinary [PipelineRunner] run with purpose
 * `EXPERIMENT` and the Sandbox outcome, so it never publishes, consumes no articles and leaves the
 * schedule alone.
 */
@Service
class ExperimentService(
    private val episodeService: EpisodeService,
    private val pipelineRunner: PipelineRunner,
    private val episodeWindowResolver: EpisodeWindowResolver,
    private val episodeScoringService: EpisodeScoringService,
    private val llmCallRepository: LlmCallRepository,
    private val appProperties: AppProperties
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // Outlives the HTTP request, like PodcastService's pipeline scope.
    private val experimentScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @PreDestroy
    fun stopExperimentScope() {
        experimentScope.cancel()
    }

    /**
     * Starts [plan] against [source]'s article set and returns the placeholder episodes at once.
     *
     * The size cap is checked first, so an oversized request is refused before any episode exists
     * or any model is called. The runs execute one after another, interleaved by repeat (every
     * variant once, then every variant again), so a variant's runs are spread over the same span of
     * time as the others' and a provider's load at one moment does not favour one variant.
     *
     * A variant that leaves `bypassLlmCache` unset bypasses the cache: otherwise every repeat after
     * the first would replay the first one's script and the repeats would show no spread.
     */
    fun startExperiment(source: Episode, podcast: Podcast, plan: ExperimentPlan): ExperimentStarted {
        val cap = appProperties.experiments.maxVariantRepeats
        if (plan.runCount > cap) {
            throw ExperimentTooLargeException(
                "${plan.variants.size} variants x ${plan.repeats} repeats is ${plan.runCount} runs, " +
                    "more than the configured maximum of $cap (app.experiments.max-variant-repeats)"
            )
        }
        val sourceId = source.id!!
        if (source.purpose == EpisodePurpose.EXPERIMENT) {
            throw EpisodeNotExperimentableException("Episode $sourceId is itself an experiment episode; experiment on its source instead")
        }
        val linked = episodeService.findLinkedArticlesAndTopics(sourceId)
        if (linked.articles.isEmpty()) {
            throw EpisodeNotExperimentableException("Episode $sourceId has no linked articles to recompose")
        }

        val window = episodeWindowResolver.windowOf(source) ?: episodeWindowResolver.resolveForNow(podcast)
        val experimentId = UUID.randomUUID().toString()
        val specs = (1..plan.repeats).flatMap { plan.variants }.map { variant ->
            val episode = episodeService.createExperimentEpisode(
                podcast, window, ExperimentRunIdentity(experimentId, variant.name, sourceId)
            )
            RunSpec(
                podcast, episode, RunPurpose.EXPERIMENT, RunInput.ArticleSet(linked, window),
                ResumePoint.COMPOSE, RunOutcome.Sandbox,
                overrides = variant.overrides.copy(bypassLlmCache = variant.overrides.bypassLlmCache ?: true)
            )
        }
        log.info("[Experiment] Starting experiment {} on episode {} of podcast '{}' ({}): {} variants x {} repeats",
            experimentId, sourceId, podcast.name, podcast.id, plan.variants.size, plan.repeats)
        experimentScope.launch { runInSequence(experimentId, specs) }
        return ExperimentStarted(experimentId, sourceId, specs.map { it.episode!!.id!! })
    }

    /** The runner handles a run's own failure; the catch only keeps one run's error from ending the rest. */
    private suspend fun runInSequence(experimentId: String, specs: List<RunSpec>) {
        for (spec in specs) {
            try {
                pipelineRunner.run(spec)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("[Experiment] Run for episode {} of experiment {} ended with an unhandled error: {}",
                    spec.episode?.id, experimentId, e.message, e)
            }
        }
        log.info("[Experiment] Experiment {} finished its {} runs", experimentId, specs.size)
    }

    /** Every experiment run against [source], grouped by experiment and variant, with per-variant means. */
    fun compare(source: Episode): SourceEpisodeExperiments {
        val experiments = episodeService.findExperimentEpisodes(source.id!!)
            .groupBy { it.experimentId ?: "" }
            .map { (experimentId, episodes) ->
                ExperimentComparison(
                    experimentId = experimentId,
                    startedAt = episodes.minOf { it.generatedAt },
                    variants = episodes.groupBy { it.experimentVariant ?: "" }
                        .map { (name, runs) -> compareVariant(name, runs.map { metricsOf(it) }) }
                )
            }
            .sortedBy { it.startedAt }
        return SourceEpisodeExperiments(source.id, experiments)
    }

    private fun compareVariant(name: String, runs: List<ExperimentRunMetrics>): ExperimentVariantComparison {
        val completed = runs.filter { it.status == EpisodeStatus.GENERATED.name }
        return ExperimentVariantComparison(
            name = name,
            completedRuns = completed.size,
            failedRuns = runs.count { it.status == EpisodeStatus.FAILED.name },
            pendingRuns = runs.count { it.status == EpisodeStatus.GENERATING.name },
            runs = runs,
            mean = ExperimentVariantMeans(
                judgeScore = completed.meanOf { it.judgeScore },
                totalCostCents = completed.meanOf { it.totalCostCents },
                composeCostCents = completed.meanOf { it.composeCostCents },
                composeDurationMs = completed.meanOf { it.composeDurationMs?.toDouble() },
                composeCalls = completed.meanOf { it.composeCalls.toDouble() },
                reasoningTokens = completed.meanOf { it.reasoningTokens?.toDouble() },
                wordCount = completed.meanOf { it.wordCount?.toDouble() }
            )
        )
    }

    private fun metricsOf(episode: Episode): ExperimentRunMetrics {
        val episodeId = episode.id!!
        val composeCalls = llmCallRepository.requestsForEpisode(episodeId).filter { it.stage == CostStage.COMPOSE }
        val judged = episodeScoringService.existingScores(episodeId).firstOrNull()
        val composed = episode.status == EpisodeStatus.GENERATED
        val composeCost = episode.composeReportedCostCents ?: episode.composeCostCents.toDouble()
        return ExperimentRunMetrics(
            episodeId = episodeId,
            status = episode.status.name,
            errorMessage = episode.errorMessage,
            judgeScore = judged?.overall,
            judgeModel = judged?.judgeModel,
            totalCostCents = if (composed) {
                composeCost + (episode.recapReportedCostCents ?: episode.recapCostCents.toDouble()) + (episode.researchCostCents ?: 0)
            } else null,
            composeCostCents = if (composed) composeCost else null,
            composeDurationMs = wallTimeMs(composeCalls),
            composeCalls = composeCalls.size,
            reasoningTokens = composeCalls.mapNotNull { it.reasoningTokens }.takeIf { it.isNotEmpty() }?.sum(),
            servedProviders = composeCalls.mapNotNull { it.servedProvider }.distinct(),
            wordCount = if (composed) spokenWords(episode.scriptText) else null
        )
    }

    private fun wallTimeMs(calls: List<LlmCallRow>): Long? {
        if (calls.isEmpty()) return null
        val start = calls.minOf { Instant.parse(it.startedAt) }
        val end = calls.maxOf { Instant.parse(it.startedAt).plusMillis(it.durationMs) }
        return Duration.between(start, end).toMillis()
    }

    /** Spoken words of a dialogue script; a script without speaker tags counts its plain words. */
    private fun spokenWords(script: String): Int =
        ScriptMetrics.of(script).totalWords.takeIf { it > 0 }
            ?: script.split(Regex("\\s+")).count { it.isNotBlank() }

    private fun <T> List<T>.meanOf(value: (T) -> Double?): Double? =
        mapNotNull(value).takeIf { it.isNotEmpty() }?.average()
}
