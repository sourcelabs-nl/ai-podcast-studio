package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.llm.CostEstimator
import com.aisummarypodcast.llm.LlmCallCost
import com.aisummarypodcast.llm.LlmPipeline
import com.aisummarypodcast.llm.ModelResolver
import com.aisummarypodcast.llm.PipelineStage
import com.aisummarypodcast.llm.RunConfig
import com.aisummarypodcast.llm.RunOverrides
import com.aisummarypodcast.llm.PreviewResult
import com.aisummarypodcast.source.SourceAggregator
import com.aisummarypodcast.store.*
import jakarta.annotation.PreDestroy
import org.springframework.data.repository.findByIdOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class PodcastService(
    private val podcastRepository: PodcastRepository,
    private val sourceRepository: SourceRepository,
    private val articleRepository: ArticleRepository,
    private val postRepository: PostRepository,
    private val postArticleRepository: PostArticleRepository,
    private val episodeArticleRepository: EpisodeArticleRepository,
    private val episodeRepository: EpisodeRepository,
    private val appProperties: AppProperties,
    private val llmPipeline: LlmPipeline,
    private val episodeService: EpisodeService,
    private val eventPublisher: ApplicationEventPublisher,
    private val sourceAggregator: SourceAggregator,
    private val episodeWindowResolver: EpisodeWindowResolver,
    private val modelResolver: ModelResolver,
    private val pipelineRunner: PipelineRunner
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // Background scope for long-running pipeline work (manual generate/regenerate, retry) that must
    // outlive the HTTP request. Running generation inside a suspend controller couples it to the
    // request lifecycle, so a Spring MVC async-request timeout would cancel the in-flight pipeline.
    private val pipelineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @PreDestroy
    fun stopPipelineScope() {
        pipelineScope.cancel()
    }

    fun detectResumePoint(episode: Episode): ResumePoint {
        if (episode.scriptText.isNotBlank()) return ResumePoint.POST_COMPOSE
        val links = episodeArticleRepository.findByEpisodeId(episode.id!!)
        if (links.isNotEmpty()) return ResumePoint.COMPOSE
        return ResumePoint.FULL_PIPELINE
    }

    fun retryEpisode(episode: Episode, podcast: Podcast): ResumePoint {
        val resumePoint = detectResumePoint(episode)
        episodeService.resetForRetry(episode)

        eventPublisher.publishEvent(
            PodcastEvent(this, podcast.id, "episode", episode.id!!, "episode.retrying",
                mapOf("resumePoint" to resumePoint.name, "episodeNumber" to episode.id))
        )

        // The episode's own window, so a retry reselects from (or announces) the period the run
        // started with. An episode from before windows were recorded falls back to now. A focus
        // episode reselects against its focus, never through the topic-scored regular path.
        val window = episodeWindowResolver.windowOf(episode) ?: episodeWindowResolver.resolveForNow(podcast)
        val input = episode.focus?.let { RunInput.Focus(it, window) } ?: RunInput.Window(window)
        launchRun(
            RunSpec(podcast, episode, RunPurpose.RETRY, input, resumePoint, RunOutcome.Deliver())
        )
        return resumePoint
    }

    fun validateTtsConfig(ttsProvider: TtsProviderType, style: PodcastStyle, ttsVoices: Map<String, String>?): String? {
        val dialogueProviders = setOf(TtsProviderType.ELEVENLABS, TtsProviderType.INWORLD)
        if (style == PodcastStyle.DIALOGUE && ttsProvider !in dialogueProviders) {
            return "Dialogue style requires ElevenLabs or Inworld as TTS provider"
        }
        if (style == PodcastStyle.DIALOGUE && (ttsVoices == null || ttsVoices.size < 2)) {
            return "Dialogue style requires at least two voice roles in ttsVoices (e.g., host and cohost)"
        }
        if (style == PodcastStyle.INTERVIEW && ttsProvider !in dialogueProviders) {
            return "Interview style requires ElevenLabs or Inworld as TTS provider"
        }
        if (style == PodcastStyle.INTERVIEW && (ttsVoices == null || ttsVoices.size < 2)) {
            return "Interview style requires at least two voice roles in ttsVoices (interviewer and expert)"
        }
        if (style == PodcastStyle.INTERVIEW && ttsVoices != null && ttsVoices.keys != setOf("interviewer", "expert")) {
            return "Interview style requires exactly 'interviewer' and 'expert' voice roles"
        }
        return null
    }

    fun create(userId: String, name: String, topic: String, podcast: Podcast? = null): Podcast {
        val newPodcast = Podcast(
            id = UUID.randomUUID().toString(),
            userId = userId,
            name = name,
            topic = topic,
            language = podcast?.language ?: "en",
            llmModels = podcast?.llmModels,
            ttsProvider = podcast?.ttsProvider ?: TtsProviderType.OPENAI,
            ttsVoices = podcast?.ttsVoices,
            ttsSettings = podcast?.ttsSettings,
            style = podcast?.style ?: PodcastStyle.NEWS_BRIEFING,
            targetWords = podcast?.targetWords,
            cron = podcast?.cron ?: "0 0 6 * * *",
            timezone = podcast?.timezone ?: "UTC",
            customInstructions = podcast?.customInstructions,
            relevanceThreshold = podcast?.relevanceThreshold ?: 5,
            requireReview = podcast?.requireReview ?: false,
            requirePublishApproval = podcast?.requirePublishApproval ?: false,
            maxLlmCostCents = podcast?.maxLlmCostCents,
            maxArticleAgeDays = podcast?.maxArticleAgeDays,
            speakerNames = podcast?.speakerNames,
            fullBodyThreshold = podcast?.fullBodyThreshold,
            sponsor = podcast?.sponsor,
            pronunciations = podcast?.pronunciations,
            recapLookbackEpisodes = podcast?.recapLookbackEpisodes,
            composeSettings = podcast?.composeSettings,
            deepDiveEnabled = podcast?.deepDiveEnabled ?: false,
            subtopics = podcast?.subtopics,
            rapidFireWeightThreshold = podcast?.rapidFireWeightThreshold ?: 3
        )
        return podcastRepository.save(newPodcast)
    }

    fun findAll(): List<Podcast> = podcastRepository.findAll().toList()

    fun findByUserId(userId: String): List<Podcast> = podcastRepository.findByUserId(userId)

    fun findById(podcastId: String): Podcast? = podcastRepository.findByIdOrNull(podcastId)

    /**
     * Eagerly aggregates and relevance-scores the podcast's non-aggregate sources so they are
     * ranked before generation. Triggered by the polling scheduler after each poll round.
     */
    suspend fun scoreReadySources(podcast: Podcast) = llmPipeline.scoreReadySources(podcast)

    fun update(podcastId: String, updates: Podcast): Podcast? {
        val existing = findById(podcastId) ?: return null
        val updated = existing.copy(
            name = updates.name,
            topic = updates.topic,
            language = updates.language,
            llmModels = updates.llmModels,
            ttsProvider = updates.ttsProvider,
            ttsVoices = updates.ttsVoices,
            ttsSettings = updates.ttsSettings,
            style = updates.style,
            targetWords = updates.targetWords,
            cron = updates.cron,
            timezone = updates.timezone,
            customInstructions = updates.customInstructions,
            relevanceThreshold = updates.relevanceThreshold,
            requireReview = updates.requireReview,
            requirePublishApproval = updates.requirePublishApproval,
            maxLlmCostCents = updates.maxLlmCostCents,
            maxArticleAgeDays = updates.maxArticleAgeDays,
            speakerNames = updates.speakerNames,
            fullBodyThreshold = updates.fullBodyThreshold,
            sponsor = updates.sponsor,
            pronunciations = updates.pronunciations,
            recapLookbackEpisodes = updates.recapLookbackEpisodes,
            composeSettings = updates.composeSettings,
            deepDiveEnabled = updates.deepDiveEnabled,
            subtopics = updates.subtopics,
            rapidFireWeightThreshold = updates.rapidFireWeightThreshold,
            rapidFireMaxItems = updates.rapidFireMaxItems
        )
        return podcastRepository.save(updated)
    }

    /** Runs the pipeline for the current window without persisting an episode. */
    suspend fun previewBriefing(podcast: Podcast, onProgress: (stage: String, detail: Map<String, Any>) -> Unit = { _, _ -> }): PreviewResult? {
        val spec = RunSpec(
            podcast, episode = null, RunPurpose.PREVIEW,
            RunInput.Window(episodeWindowResolver.resolveForNow(podcast)),
            ResumePoint.FULL_PIPELINE, RunOutcome.Transient(onProgress)
        )
        return when (val result = pipelineRunner.run(spec)) {
            is RunResult.Previewed -> result.preview
            else -> error("A transient run ended as $result")
        }
    }

    /**
     * Generates a briefing for [window]. The caller decides the window, because only it knows which
     * scheduled slot is being served; [EpisodeWindowResolver.resolveForNow] covers an ad-hoc run.
     */
    suspend fun generateBriefing(
        podcast: Podcast,
        window: EpisodeWindow = episodeWindowResolver.resolveForNow(podcast)
    ): GenerateBriefingResult {
        if (episodeService.hasActiveEpisode(podcast.id)) {
            log.info("Podcast '{}' ({}) has an active episode (generating/pending/approved) — skipping generation", podcast.name, podcast.id)
            return GenerateBriefingResult(episode = null)
        }

        val generatingEpisode = episodeService.createGeneratingEpisode(podcast, window)
        val spec = RunSpec(
            podcast, generatingEpisode, RunPurpose.SCHEDULED, RunInput.Window(window),
            ResumePoint.FULL_PIPELINE, RunOutcome.Deliver()
        )
        return when (val result = pipelineRunner.run(spec)) {
            is RunResult.Completed -> GenerateBriefingResult(episode = result.episode)
            is RunResult.NothingToCompose -> GenerateBriefingResult(episode = null)
            is RunResult.Failed -> GenerateBriefingResult(episode = result.episode, failed = true, errorMessage = result.errorMessage)
            is RunResult.Previewed -> error("A delivered run ended as $result")
        }
    }

    /**
     * Starts briefing generation in the background and returns the GENERATING episode immediately,
     * or null if one is already active. Decouples the multi-minute pipeline from the HTTP request so
     * a Spring MVC async-request timeout cannot cancel the in-flight generation; progress and
     * completion are delivered to the UI via SSE events.
     */
    fun generateBriefingAsync(podcast: Podcast, focus: String? = null): Episode? {
        val focusText = focus?.trim()?.takeIf { it.isNotEmpty() }
        if (episodeService.hasActiveEpisode(podcast.id, focusEpisodes = focusText != null)) {
            log.info("Podcast '{}' ({}) has an active {} episode — skipping manual generation",
                podcast.name, podcast.id, if (focusText != null) "focus" else "regular")
            return null
        }
        val window = episodeWindowResolver.resolveForNow(podcast)
        if (focusText != null) {
            // A focus episode is scored against its focus rather than the podcast's topic, keeps only
            // the relevant articles and is composed with research forced on. It always stops at
            // review (see [EpisodeService.finalizeEpisode]).
            val generatingEpisode = episodeService.createGeneratingEpisode(
                podcast, window, updateLastGenerated = false, focus = focusText
            )
            launchRun(
                RunSpec(
                    podcast, generatingEpisode, RunPurpose.FOCUS, RunInput.Focus(focusText, window),
                    ResumePoint.FULL_PIPELINE, RunOutcome.Deliver()
                )
            )
            return generatingEpisode
        }
        val generatingEpisode = episodeService.createGeneratingEpisode(podcast, window)
        launchRun(
            RunSpec(
                podcast, generatingEpisode, RunPurpose.MANUAL, RunInput.Window(window),
                ResumePoint.FULL_PIPELINE, RunOutcome.Deliver()
            )
        )
        return generatingEpisode
    }

    /**
     * Recomposes a focus episode under review with a reviewer's [feedback], in the background: the
     * same locked article set, research rerun (the plan and searches replay from their caches, and the
     * new run replaces the recorded sources), and the result written onto the same episode, which
     * stays in review. Repeatable; the episode keeps only the latest feedback.
     */
    fun recomposeFocusEpisodeAsync(episode: Episode, podcast: Podcast, feedback: String): Episode {
        if (episode.focus == null || episode.status != EpisodeStatus.PENDING_REVIEW) {
            throw EpisodeNotRecomposableException(
                "Episode ${episode.id} is not a focus episode awaiting review, so it cannot be recomposed with feedback"
            )
        }
        val marked = episodeService.markRecomposing(episode)
        val window = episodeWindowResolver.windowOf(episode) ?: episodeWindowResolver.resolveForNow(podcast)
        launchRun(
            RunSpec(
                podcast, marked, RunPurpose.RECOMPOSE, RunInput.Focus(episode.focus, window),
                ResumePoint.COMPOSE, RunOutcome.Review(feedback)
            )
        )
        return marked
    }

    /**
     * Runs [spec] on the background scope. The runner handles a run's own failure; this catch only
     * keeps an error in that handling from escaping the launch.
     */
    private fun launchRun(spec: RunSpec) {
        pipelineScope.launch {
            try {
                pipelineRunner.run(spec)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("[Pipeline] {} run for episode {} (podcast '{}' ({})) ended with an unhandled error: {}",
                    spec.purpose, spec.episode?.id, spec.podcast.name, spec.podcast.id, e.message, e)
            }
        }
    }

    /**
     * Re-runs the article window of an existing failed or discarded episode as a fresh episode,
     * returning the new GENERATING episode immediately.
     *
     * This is how a past day is reproduced. The window comes from the source episode, so the run
     * selects the same period rather than whatever period is current, and the source episode is
     * left untouched: the re-run is a new episode with its own status and publications.
     *
     * Selection still requires the articles to be unused, which discarding an episode restores, so
     * only a failed or discarded episode can be re-run and a published day has to be discarded
     * first.
     *
     * `updateLastGenerated = false`: a re-run of a past window must not satisfy today's cron slot.
     */
    fun rerunEpisodeAsync(sourceEpisode: Episode, podcast: Podcast): Episode {
        if (sourceEpisode.status != EpisodeStatus.FAILED && sourceEpisode.status != EpisodeStatus.DISCARDED) {
            throw EpisodeNotRerunnableException(
                "Episode ${sourceEpisode.id} is ${sourceEpisode.status}; only a failed or discarded " +
                    "episode can be re-run, so discard this one first"
            )
        }

        val window = episodeWindowResolver.windowOf(sourceEpisode)
            ?: throw EpisodeNotRerunnableException(
                "Episode ${sourceEpisode.id} carries no article window, so the period it covered is " +
                    "unknown, so generate a new episode instead"
            )

        val generatingEpisode = episodeService.createGeneratingEpisode(podcast, window, updateLastGenerated = false)
        log.info("[Pipeline] Re-running window {} of episode {} as episode {} for podcast '{}' ({})",
            window, sourceEpisode.id, generatingEpisode.id, podcast.name, podcast.id)
        launchRun(
            RunSpec(
                podcast, generatingEpisode, RunPurpose.RERUN, RunInput.Window(window),
                ResumePoint.FULL_PIPELINE, RunOutcome.Deliver(updateLastGenerated = false)
            )
        )
        return generatingEpisode
    }

    /**
     * Starts episode regeneration in the background and returns the GENERATING episode immediately.
     * Like [generateBriefingAsync], this decouples the recompose + TTS work from the HTTP request so
     * a request timeout cannot cancel it. `updateLastGenerated = false`: regeneration must not bump
     * the podcast's lastGeneratedAt or the scheduler would skip the next scheduled run.
     *
     * [bypassLlmCache] makes this an evaluation run. Regeneration recomposes the same articles, so
     * repeating it is how one prompt variant is sampled k times; without the bypass the second and
     * later repetitions replay the first one's script and the sample has no spread. Such a run also
     * records the conditions it ran under (see `EvaluationRunRecorder`).
     */
    fun regenerateEpisodeAsync(
        sourceEpisode: Episode,
        podcast: Podcast,
        bypassLlmCache: Boolean = false
    ): Episode {
        // Check before creating anything. Regeneration recomposes from the source episode's linked
        // articles, so an episode that failed before article selection can never be regenerated —
        // creating the episode first only manufactured a second FAILED episode per attempt.
        val linked = episodeService.findLinkedArticlesAndTopics(sourceEpisode.id!!)
        if (linked.articles.isEmpty()) {
            throw EpisodeNotRegenerableException(
                "Episode ${sourceEpisode.id} has no linked articles to recompose — it failed before " +
                    "article selection, so it needs a fresh generation rather than a regeneration"
            )
        }

        // The regenerated episode covers the same period as the one it recomposes.
        val window = episodeWindowResolver.windowOf(sourceEpisode) ?: episodeWindowResolver.resolveForNow(podcast)
        val generatingEpisode = episodeService.createGeneratingEpisode(podcast, window, updateLastGenerated = false)
        launchRun(
            RunSpec(
                podcast, generatingEpisode, RunPurpose.REGENERATE, RunInput.ArticleSet(linked, window),
                ResumePoint.COMPOSE,
                RunOutcome.Deliver(updateLastGenerated = false, generatedAt = sourceEpisode.generatedAt),
                overrides = if (bypassLlmCache) RunOverrides(bypassLlmCache = true) else null
            )
        )
        return generatingEpisode
    }

    /**
     * The posts an article was aggregated from, oldest first, or null when the article does not
     * exist or belongs to a source of another podcast. Ownership is checked here rather than in the
     * controller so article contents follow the same rules as every other read.
     */
    fun findArticlePosts(podcast: Podcast, articleId: Long): List<ArticlePostResponse>? {
        val article = articleRepository.findByIdOrNull(articleId) ?: return null
        val sourceIds = sourceRepository.findByPodcastId(podcast.id).map { it.id }
        if (article.sourceId !in sourceIds) return null

        return postRepository.findPostsByArticleId(articleId).map { post ->
            ArticlePostResponse(
                id = post.id!!,
                title = post.title,
                body = post.body,
                url = post.url,
                publishedAt = post.publishedAt
            )
        }
    }

    fun getUpcomingContent(podcast: Podcast): UpcomingContent {
        val sources = sourceRepository.findByPodcastId(podcast.id)
        val sourceIds = sources.map { it.id }
        if (sourceIds.isEmpty()) return UpcomingContent(emptyList(), emptyList(), sources, 0, 0)

        val since = podcast.lastGeneratedAt ?: Instant.now().minus(
            (podcast.maxArticleAgeDays ?: appProperties.source.maxArticleAgeDays).toLong(), ChronoUnit.DAYS
        ).toString()

        val articles = articleRepository.findUnprocessedSince(sourceIds, since)
        val unlinkedPosts = postRepository.findUnlinkedSince(sourceIds, since)

        val articleIds = articles.map { it.id!! }
        val linkedPostCount = if (articleIds.isNotEmpty()) postArticleRepository.countByArticleIds(articleIds) else 0L
        val totalPostCount = linkedPostCount + unlinkedPosts.size

        val sourceMap = sources.associateBy { it.id }
        val unlinkedPostArticleCount = unlinkedPosts
            .groupBy { it.sourceId }
            .entries
            .sumOf { (sourceId, posts) ->
                val source = sourceMap[sourceId]
                if (source != null && sourceAggregator.shouldAggregate(source) && posts.size > 1) 1L else posts.size.toLong()
            }
        val effectiveArticleCount = articles.size.toLong() + unlinkedPostArticleCount

        return UpcomingContent(
            articles, unlinkedPosts, sources, totalPostCount, effectiveArticleCount,
            postCounts = if (articleIds.isNotEmpty()) postRepository.getPostCountsByArticleIds(articleIds) else emptyMap(),
            scoringSpend = scoringSpend(podcast, articles)
        )
    }

    /**
     * What has already been spent scoring [articles], totalled the way an episode's score stage is:
     * reported per-article costs summed, and the articles that reported nothing estimated together
     * from their tokens. No article is scored to answer this; the figures are already on them.
     */
    private fun scoringSpend(podcast: Podcast, articles: List<Article>): ScoringSpend {
        val filterModel = modelResolver.resolve(RunConfig.resolve(appProperties, podcast), PipelineStage.FILTER)
        val cost = CostEstimator.aggregateStageCost(
            articles.map { LlmCallCost(it.llmInputTokens ?: 0, it.llmOutputTokens ?: 0, it.llmReportedCostUsd) },
            filterModel.cost
        )
        return ScoringSpend(
            model = filterModel.model,
            calls = articles.size,
            inputTokens = articles.sumOf { it.llmInputTokens ?: 0 },
            outputTokens = articles.sumOf { it.llmOutputTokens ?: 0 },
            costCents = cost.costCents ?: 0.0
        )
    }

    @Transactional
    fun delete(podcastId: String): Boolean {
        val podcast = findById(podcastId) ?: return false
        deletePodcastCascade(podcast)
        return true
    }

    @Transactional
    fun deleteAllByUserId(userId: String) {
        val podcasts = podcastRepository.findByUserId(userId)
        for (podcast in podcasts) {
            deletePodcastCascade(podcast)
        }
    }

    private fun deletePodcastCascade(podcast: Podcast) {
        val sources = sourceRepository.findByPodcastId(podcast.id)
        for (source in sources) {
            articleRepository.deleteBySourceId(source.id)
            sourceRepository.delete(source)
        }

        val episodes = episodeRepository.findByPodcastId(podcast.id)
        for (episode in episodes) {
            episode.audioFilePath?.let { filePath ->
                try {
                    val audioPath = Path.of(filePath)
                    if (Files.exists(audioPath)) {
                        Files.delete(audioPath)
                    }
                } catch (e: Exception) {
                    log.error("Failed to delete audio file for episode {}: {}", episode.id, e.message)
                }
            }
            episodeRepository.delete(episode)
        }

        podcastRepository.delete(podcast)
        log.info("Deleted podcast {} and cascaded to {} sources, {} episodes", podcast.id, sources.size, episodes.size)
    }
}
