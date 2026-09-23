package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.llm.ComposeContext
import com.aisummarypodcast.llm.FilteredArticle
import com.aisummarypodcast.llm.LlmPipeline
import com.aisummarypodcast.llm.RunConfig
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.Podcast
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper

private typealias ProgressCallback = (stage: String, detail: Map<String, Any>) -> Unit

/**
 * The only way the pipeline is run. A route describes its run as a [RunSpec]; the runner sequences
 * the stages [LlmPipeline] implements for that spec's input and resume point, then applies the
 * outcome, which is also where the shared epilogue (judge, recap, show notes, sources) runs.
 *
 * The run's [RunConfig] is resolved once, from the app defaults, the podcast and the spec's
 * overrides, and passed to every stage. A run that writes to an episode records its purpose and
 * that configuration on the episode before the first stage.
 *
 * A run that writes to an episode never throws, except for cancellation: a failure is handled per
 * purpose and returned as [RunResult.Failed]. A [RunOutcome.Transient] run lets its failure
 * propagate, since there is no episode to record it on and its caller reports it.
 */
@Service
class PipelineRunner(
    private val llmPipeline: LlmPipeline,
    private val episodeService: EpisodeService,
    private val episodeWindowResolver: EpisodeWindowResolver,
    private val eventPublisher: ApplicationEventPublisher,
    private val appProperties: AppProperties,
    private val jsonMapper: JsonMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun run(spec: RunSpec): RunResult {
        val config = RunConfig.resolve(appProperties, spec.podcast, spec.overrides)
        val outcome = spec.outcome
        if (outcome is RunOutcome.Transient) return preview(spec, config, outcome)

        val episode = spec.episode!!
        return try {
            episodeService.recordRun(episode.id!!, spec.purpose.episodePurpose()!!, jsonMapper.writeValueAsString(config))
            execute(spec, config, episode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(spec, episode, e)
        }
    }

    private suspend fun preview(spec: RunSpec, config: RunConfig, outcome: RunOutcome.Transient): RunResult {
        val input = spec.input as? RunInput.Window
            ?: throw IllegalArgumentException("A transient run selects from a window, not ${spec.input}")
        return RunResult.Previewed(llmPipeline.preview(spec.podcast, config, input.window, outcome.onProgress))
    }

    private suspend fun execute(spec: RunSpec, config: RunConfig, episode: Episode): RunResult {
        val onProgress = progressReporter(spec, episode.id!!)
        if (spec.resumePoint == ResumePoint.POST_COMPOSE) {
            val topicLabels = episodeService.findLinkedArticlesAndTopics(episode.id).topicLabels
            return RunResult.Completed(episodeService.finalizeEpisode(episode, spec.podcast, topicLabels))
        }
        return when (val input = spec.input) {
            is RunInput.Focus -> composeFocus(spec, config, episode, input, onProgress)
            is RunInput.ArticleSet -> recomposeArticleSet(spec, config, episode, input, onProgress)
            is RunInput.Window ->
                if (spec.resumePoint == ResumePoint.FULL_PIPELINE) selectAndCompose(spec, config, episode, input.window, onProgress)
                else composeLinked(spec, config, episode, input.window, onProgress)
        }
    }

    /**
     * The regular path: aggregate, score, dedup and compose from [window]. A retry passes no recent
     * focus episodes to the prompt and reports only the stages [LlmPipeline] reports, and an empty
     * selection fails it, where any other run deletes its placeholder episode instead.
     */
    private suspend fun selectAndCompose(
        spec: RunSpec,
        config: RunConfig,
        episode: Episode,
        window: EpisodeWindow,
        onProgress: ProgressCallback
    ): RunResult {
        val podcast = spec.podcast
        val isRetry = spec.purpose == RunPurpose.RETRY
        val eligible = llmPipeline.aggregateScoreAndFilter(podcast, config, window, episode.id, onProgress)
            ?: return nothingSelected(spec, episode, "No eligible articles for retry in window $window")
        val dedupResult = llmPipeline.dedup(eligible, podcast, config, episode.id, onProgress)
            ?: return nothingSelected(spec, episode, "All articles filtered as duplicates during retry")

        episodeService.saveDedupResults(episode, dedupResult)
        if (!isRetry) stageEvent(podcast, episode, "dedup_saved", mapOf("articleCount" to dedupResult.filteredArticles.size))

        val composeResult = llmPipeline.compose(
            dedupResult.filteredArticles, podcast,
            ComposeContext(
                followUpAnnotations = dedupResult.followUpAnnotations,
                topicLabels = dedupResult.topicLabels,
                episodeDate = episodeWindowResolver.episodeDateOf(podcast, window),
                episodeId = episode.id,
                recentFocusEpisodes = if (isRetry) emptyList() else episodeService.findRecentFocusEpisodes(podcast.id),
                runConfig = config
            ),
            onProgress
        )
        episodeService.saveComposeResult(episode, composeResult)
        if (!isRetry) {
            stageEvent(podcast, episode, "script_saved")
            stageEvent(podcast, episode, "marking_processed", mapOf("articleCount" to dedupResult.filteredArticles.size))
            stageEvent(podcast, episode, "generating_recap")
        }
        return RunResult.Completed(
            episodeService.finalizeEpisode(episode, podcast, composeResult.topicOrder, deliverOutcome(spec).updateLastGenerated)
        )
    }

    /** The Deliver outcome of a run that reaches finalize; only a Deliver run does. */
    private fun deliverOutcome(spec: RunSpec): RunOutcome.Deliver =
        spec.outcome as? RunOutcome.Deliver
            ?: throw IllegalArgumentException("A finalized run is delivered, not ${spec.outcome}")

    private fun nothingSelected(spec: RunSpec, episode: Episode, retryFailure: String): RunResult {
        if (spec.purpose == RunPurpose.RETRY) throw IllegalStateException(retryFailure)
        episodeService.deleteGeneratingEpisode(episode.id!!)
        return RunResult.NothingToCompose
    }

    /** A retry resuming at compose: the episode's own linked articles, composed with their dedup topics. */
    private suspend fun composeLinked(
        spec: RunSpec,
        config: RunConfig,
        episode: Episode,
        window: EpisodeWindow,
        onProgress: ProgressCallback
    ): RunResult {
        val podcast = spec.podcast
        val (articles, topicLabels, articleTopics) = episodeService.findLinkedArticlesAndTopics(episode.id!!)
        val composeResult = llmPipeline.compose(
            articles.map { FilteredArticle(it, topic = articleTopics[it.id]) }, podcast,
            ComposeContext(
                topicLabels = topicLabels,
                episodeDate = episodeWindowResolver.episodeDateOf(podcast, window),
                episodeId = episode.id,
                runConfig = config
            ),
            onProgress
        )
        episodeService.saveComposeResult(episode, composeResult)
        return RunResult.Completed(
            episodeService.finalizeEpisode(episode, podcast, composeResult.topicOrder, deliverOutcome(spec).updateLastGenerated)
        )
    }

    /**
     * A focus run: select against the focus from the window, or rescore the episode's linked set
     * against it when resuming at compose (the focus summaries were never persisted, and the LLM
     * cache replays what the first run paid for), then compose with the focus.
     */
    private suspend fun composeFocus(
        spec: RunSpec,
        config: RunConfig,
        episode: Episode,
        input: RunInput.Focus,
        onProgress: ProgressCallback
    ): RunResult {
        val podcast = spec.podcast
        val episodeId = episode.id!!
        val selection = if (spec.resumePoint == ResumePoint.FULL_PIPELINE) {
            llmPipeline.selectForFocus(podcast, config, input.window, input.text, episodeId, onProgress)
                .also { episodeService.saveFocusSelection(episode, it) }
        } else {
            llmPipeline.scoreForFocus(
                podcast, config, episodeService.findLinkedArticlesAndTopics(episodeId).articles, input.text, episodeId, onProgress
            )
        }
        val outcome = spec.outcome
        val composeResult = llmPipeline.compose(
            selection.articles, podcast,
            ComposeContext(
                episodeDate = episodeWindowResolver.episodeDateOf(podcast, input.window),
                episodeId = episodeId,
                focus = input.text,
                extraInstruction = (outcome as? RunOutcome.Review)?.feedback,
                runConfig = config
            ),
            onProgress
        )
        if (outcome !is RunOutcome.Review) {
            episodeService.saveComposeResult(episode, composeResult)
            return RunResult.Completed(
                episodeService.finalizeEpisode(episode, podcast, composeResult.topicOrder, deliverOutcome(spec).updateLastGenerated)
            )
        }

        val updated = episodeService.saveFeedbackRecompose(episode, composeResult, outcome.feedback)
        val finalEpisode = episodeService.runEpilogueForRewrite(updated, podcast)
        eventPublisher.publishEvent(
            PodcastEvent(this, podcast.id, "episode", episodeId, "episode.created", mapOf("episodeNumber" to episodeId))
        )
        log.info("[Pipeline] Recomposed focus episode {} with feedback for podcast '{}' ({})", episodeId, podcast.name, podcast.id)
        return RunResult.Completed(finalEpisode)
    }

    /**
     * A regeneration or an experiment: the source episode's articles recomposed with their stored
     * topics and follow-up annotations (no dedup runs, so these are the only continuity signal),
     * delivered as a new episode, or kept in the sandbox.
     */
    private suspend fun recomposeArticleSet(
        spec: RunSpec,
        config: RunConfig,
        episode: Episode,
        input: RunInput.ArticleSet,
        onProgress: ProgressCallback
    ): RunResult {
        val podcast = spec.podcast
        val (articles, topicLabels, articleTopics, followUpAnnotations) = input.linked
        val context = ComposeContext(
            followUpAnnotations = followUpAnnotations,
            topicLabels = topicLabels,
            episodeDate = episodeWindowResolver.episodeDateOf(podcast, input.window),
            episodeId = episode.id,
            runConfig = config
        )
        val result = llmPipeline.recompose(articles, podcast, context, onProgress).copy(articleTopics = articleTopics)
        val outcome = spec.outcome
        if (outcome is RunOutcome.Sandbox) {
            return RunResult.Completed(episodeService.finalizeSandboxEpisode(podcast, result, episode))
        }
        if (outcome !is RunOutcome.Deliver) {
            throw IllegalArgumentException("A recomposed article set is delivered or sandboxed, not $outcome")
        }
        return RunResult.Completed(
            episodeService.createEpisodeFromPipelineResult(
                podcast,
                result,
                generatingEpisode = episode,
                overrideGeneratedAt = outcome.generatedAt,
                updateLastGenerated = outcome.updateLastGenerated
            )
        )
    }

    /**
     * Emits every stage as an SSE event. Every run but a regeneration also persists the stage on
     * the episode, on a transition only: per-article scoring reports "scoring" repeatedly.
     */
    private fun progressReporter(spec: RunSpec, episodeId: Long): ProgressCallback {
        val persistStage = spec.purpose != RunPurpose.REGENERATE
        var lastStage: String? = null
        return { stage, detail ->
            if (persistStage && stage != lastStage) {
                episodeService.updatePipelineStage(episodeId, stage)
                lastStage = stage
            }
            eventPublisher.publishEvent(
                PodcastEvent(this, spec.podcast.id, "episode", episodeId, "episode.stage", detail + ("stage" to stage))
            )
        }
    }

    private fun stageEvent(podcast: Podcast, episode: Episode, stage: String, detail: Map<String, Any> = emptyMap()) {
        eventPublisher.publishEvent(
            PodcastEvent(this, podcast.id, "episode", episode.id!!, "episode.stage", mapOf("stage" to stage) + detail)
        )
    }

    /**
     * A failed feedback recompose leaves the episode as it was, since its previous script is still a
     * valid one to review. Any other failed run fails its episode.
     */
    private fun fail(spec: RunSpec, episode: Episode, e: Exception): RunResult {
        val podcast = spec.podcast
        log.error("[Pipeline] {} run failed for episode {} (podcast '{}' ({})): {}",
            spec.purpose, episode.id, podcast.name, podcast.id, e.message, e)
        if (spec.outcome is RunOutcome.Review) {
            episodeService.clearPipelineStage(episode.id!!)
            eventPublisher.publishEvent(
                PodcastEvent(this, podcast.id, "episode", episode.id, "episode.recompose_failed",
                    mapOf("episodeNumber" to episode.id, "error" to (e.message ?: "Unknown error")))
            )
            return RunResult.Failed(episode = null, errorMessage = e.message)
        }
        val failed = episodeService.failEpisode(podcast, e.message ?: "Unknown error", episode)
        return RunResult.Failed(episode = failed, errorMessage = e.message)
    }
}
