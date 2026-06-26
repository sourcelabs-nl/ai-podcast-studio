package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.llm.FilteredArticle
import com.aisummarypodcast.llm.LlmPipeline
import com.aisummarypodcast.llm.PreviewResult
import com.aisummarypodcast.source.SourceAggregator
import com.aisummarypodcast.store.*
import jakarta.annotation.PreDestroy
import org.springframework.data.repository.findByIdOrNull
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
    private val sourceAggregator: SourceAggregator
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val retryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @PreDestroy
    fun stopRetryScope() {
        retryScope.cancel()
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

        retryScope.launch {
            try {
                doRetry(episode, podcast, resumePoint)
            } catch (e: Exception) {
                log.error("[Pipeline] Retry failed for episode {} (podcast '{}' ({})): {}", episode.id, podcast.name, podcast.id, e.message, e)
                episodeService.failEpisode(podcast, e.message ?: "Unknown error", episode)
            }
        }

        return resumePoint
    }

    private suspend fun doRetry(episode: Episode, podcast: Podcast, resumePoint: ResumePoint) {
        // The stage may be reported repeatedly within a stage (e.g. per-article scoring progress);
        // only persist the pipeline stage on an actual transition, but always emit the event so the
        // frontend can render live progress. A benign race on the first tick is harmless.
        val episodeId = episode.id!!
        var lastStage: String? = null
        val onProgress = { stage: String, detail: Map<String, Any> ->
            if (stage != lastStage) {
                episodeService.updatePipelineStage(episodeId, stage)
                lastStage = stage
            }
            eventPublisher.publishEvent(
                PodcastEvent(this, podcast.id, "episode", episodeId, "episode.stage",
                    detail + ("stage" to stage))
            )
        }

        when (resumePoint) {
            ResumePoint.FULL_PIPELINE -> {
                val eligible = llmPipeline.aggregateScoreAndFilter(podcast, onProgress)
                    ?: throw IllegalStateException("No eligible articles for retry")

                val dedupResult = llmPipeline.dedup(eligible, podcast, onProgress)
                    ?: throw IllegalStateException("All articles filtered as duplicates during retry")

                episodeService.saveDedupResults(episode, dedupResult)

                val composeResult = llmPipeline.compose(
                    dedupResult.filteredArticles, podcast,
                    dedupResult.followUpAnnotations, dedupResult.topicLabels, onProgress
                )
                episodeService.saveComposeResult(episode, composeResult)
                episodeService.finalizeEpisode(episode, podcast, composeResult.topicOrder)
            }
            ResumePoint.COMPOSE -> {
                val (articles, topicLabels, articleTopics) = episodeService.findLinkedArticlesAndTopics(episode.id!!)
                val filteredArticles = articles.map { article ->
                    FilteredArticle(article, topic = articleTopics[article.id])
                }

                val composeResult = llmPipeline.compose(filteredArticles, podcast, topicLabels = topicLabels, onProgress = onProgress)
                episodeService.saveComposeResult(episode, composeResult)
                episodeService.finalizeEpisode(episode, podcast, composeResult.topicOrder)
            }
            ResumePoint.POST_COMPOSE -> {
                val (_, topicLabels, _) = episodeService.findLinkedArticlesAndTopics(episode.id!!)
                episodeService.finalizeEpisode(episode, podcast, topicLabels)
            }
        }
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

    suspend fun previewBriefing(podcast: Podcast, onProgress: (stage: String, detail: Map<String, Any>) -> Unit = { _, _ -> }): PreviewResult? {
        return llmPipeline.preview(podcast, onProgress)
    }

    suspend fun generateBriefing(podcast: Podcast): GenerateBriefingResult {
        if (episodeService.hasActiveEpisode(podcast.id)) {
            log.info("Podcast '{}' ({}) has an active episode (generating/pending/approved) — skipping generation", podcast.name, podcast.id)
            return GenerateBriefingResult(episode = null)
        }

        val generatingEpisode = episodeService.createGeneratingEpisode(podcast)

        return try {
            // Only persist the pipeline stage on an actual transition; per-article scoring progress
            // reports "scoring" repeatedly. Always emit the event so the frontend shows live progress.
            var lastStage: String? = null
            val onProgress = { stage: String, detail: Map<String, Any> ->
                if (stage != lastStage) {
                    episodeService.updatePipelineStage(generatingEpisode.id!!, stage)
                    lastStage = stage
                }
                eventPublisher.publishEvent(
                    PodcastEvent(this, podcast.id, "episode", generatingEpisode.id!!, "episode.stage",
                        detail + ("stage" to stage))
                )
            }

            // Stage 1-2: Aggregate, score, find eligible articles
            val eligible = llmPipeline.aggregateScoreAndFilter(podcast, onProgress) ?: run {
                episodeService.deleteGeneratingEpisode(generatingEpisode.id!!)
                return GenerateBriefingResult(episode = null)
            }

            // Stage 3: Dedup filter
            val dedupResult = llmPipeline.dedup(eligible, podcast, onProgress) ?: run {
                episodeService.deleteGeneratingEpisode(generatingEpisode.id!!)
                return GenerateBriefingResult(episode = null)
            }

            // Persist dedup results (article links + topics)
            episodeService.saveDedupResults(generatingEpisode, dedupResult)
            eventPublisher.publishEvent(
                PodcastEvent(this, podcast.id, "episode", generatingEpisode.id!!, "episode.stage",
                    mapOf("stage" to "dedup_saved", "articleCount" to dedupResult.filteredArticles.size))
            )

            // Stage 4: Compose script
            val composeResult = llmPipeline.compose(
                dedupResult.filteredArticles, podcast,
                dedupResult.followUpAnnotations, dedupResult.topicLabels, onProgress
            )

            // Persist script
            episodeService.saveComposeResult(generatingEpisode, composeResult)
            eventPublisher.publishEvent(
                PodcastEvent(this, podcast.id, "episode", generatingEpisode.id!!, "episode.stage",
                    mapOf("stage" to "script_saved"))
            )

            // Finalize: set status, mark processed, recap, sources
            val articleCount = dedupResult.filteredArticles.size
            eventPublisher.publishEvent(
                PodcastEvent(this, podcast.id, "episode", generatingEpisode.id!!, "episode.stage",
                    mapOf("stage" to "marking_processed", "articleCount" to articleCount))
            )
            eventPublisher.publishEvent(
                PodcastEvent(this, podcast.id, "episode", generatingEpisode.id!!, "episode.stage",
                    mapOf("stage" to "generating_recap"))
            )
            val episode = episodeService.finalizeEpisode(generatingEpisode, podcast, composeResult.topicOrder)
            GenerateBriefingResult(episode = episode)
        } catch (e: Exception) {
            log.error("[Pipeline] Briefing generation failed for podcast '{}' ({}): {}", podcast.name, podcast.id, e.message, e)
            val failedEpisode = episodeService.failEpisode(podcast, e.message ?: "Unknown error", generatingEpisode)
            GenerateBriefingResult(episode = failedEpisode, failed = true, errorMessage = e.message)
        }
    }

    suspend fun regenerateEpisode(sourceEpisode: Episode, podcast: Podcast): Episode {
        val (articles, topicLabels, articleTopics) = episodeService.findLinkedArticlesAndTopics(sourceEpisode.id!!)
        if (articles.isEmpty()) {
            throw IllegalStateException("No articles found for episode ${sourceEpisode.id}")
        }

        val result = llmPipeline.recompose(articles, podcast, topicLabels) { stage, detail ->
            eventPublisher.publishEvent(
                PodcastEvent(this, podcast.id, "pipeline", 0, "pipeline.progress",
                    detail + ("stage" to stage))
            )
        }
        val resultWithTopics = result.copy(articleTopics = articleTopics)
        return episodeService.createEpisodeFromPipelineResult(
            podcast,
            resultWithTopics,
            overrideGeneratedAt = sourceEpisode.generatedAt,
            updateLastGenerated = false
        )
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

        return UpcomingContent(articles, unlinkedPosts, sources, totalPostCount, effectiveArticleCount)
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
