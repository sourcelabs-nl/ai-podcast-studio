package com.aisummarypodcast.podcast

import com.aisummarypodcast.llm.ArticleEligibilityService
import com.aisummarypodcast.llm.ComposeStageResult
import com.aisummarypodcast.llm.DedupStageResult
import com.aisummarypodcast.llm.EpisodeRecapGenerator
import com.aisummarypodcast.llm.ModelResolver
import com.aisummarypodcast.llm.PipelineResult
import com.aisummarypodcast.llm.PipelineStage
import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.ArticleRepository
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeArticle
import com.aisummarypodcast.store.EpisodeArticleRepository
import com.aisummarypodcast.store.EpisodeRepository
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastRepository
import com.aisummarypodcast.store.PostArticleRepository
import com.aisummarypodcast.tts.TtsPipeline
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.data.repository.findByIdOrNull
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

@Service
class EpisodeService(
    private val episodeRepository: EpisodeRepository,
    private val podcastRepository: PodcastRepository,
    private val ttsPipeline: TtsPipeline,
    private val episodeArticleRepository: EpisodeArticleRepository,
    private val articleRepository: ArticleRepository,
    private val episodeRecapGenerator: EpisodeRecapGenerator,
    private val modelResolver: ModelResolver,
    private val postArticleRepository: PostArticleRepository,
    private val episodeSourcesGenerator: EpisodeSourcesGenerator,
    private val articleEligibilityService: ArticleEligibilityService,
    private val eventPublisher: ApplicationEventPublisher,
    private val audioGenerationService: AudioGenerationService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    @EventListener(ApplicationReadyEvent::class)
    fun cleanupStaleGeneratingEpisodes() {
        val stale = episodeRepository.findByStatus(EpisodeStatus.GENERATING) +
            episodeRepository.findByStatus(EpisodeStatus.GENERATING_AUDIO)
        if (stale.isNotEmpty()) {
            log.info("[Startup] Found {} stale GENERATING/GENERATING_AUDIO episodes, marking as FAILED", stale.size)
            for (episode in stale) {
                episodeRepository.save(
                    episode.copy(
                        status = EpisodeStatus.FAILED,
                        errorMessage = "Generation interrupted by application restart"
                    )
                )
            }
        }
    }

    @Transactional
    fun createGeneratingEpisode(podcast: Podcast, updateLastGenerated: Boolean = true): Episode {
        val now = Instant.now().toString()
        val episode = episodeRepository.save(
            Episode(
                podcastId = podcast.id,
                generatedAt = now,
                scriptText = "",
                status = EpisodeStatus.GENERATING
            )
        )
        // Regeneration of an existing episode must not bump lastGeneratedAt, or the scheduler would
        // treat the cron slot as already satisfied and skip the next scheduled generation.
        if (updateLastGenerated) {
            podcastRepository.save(podcast.copy(lastGeneratedAt = now))
        }
        log.info("[Pipeline] Created GENERATING episode {} for podcast '{}' ({})", episode.id, podcast.name, podcast.id)
        eventPublisher.publishEvent(
            PodcastEvent(this, podcast.id, "episode", episode.id!!, "episode.generating", emptyMap())
        )
        return episode
    }

    /**
     * Deletes a GENERATING placeholder episode that never produced any content (e.g. when no
     * eligible articles were found). Owns the episode lifecycle so callers don't touch the
     * repository directly (Rule A2). No-op if the episode no longer exists.
     */
    @Transactional
    fun deleteGeneratingEpisode(episodeId: Long) {
        val episode = episodeRepository.findByIdOrNull(episodeId) ?: return
        episodeRepository.delete(episode)
        log.info("[Pipeline] Deleted empty GENERATING episode {}", episodeId)
    }

    fun updatePipelineStage(episodeId: Long, stage: String) {
        val episode = episodeRepository.findByIdOrNull(episodeId) ?: return
        episodeRepository.save(episode.copy(pipelineStage = stage))
    }

    /**
     * NOT @Transactional: this method runs the slow TTS pipeline and the recap LLM call between its
     * database writes. A transaction would pin a JDBC connection (and a SQLite write lock) for the
     * entire TTS+LLM duration — minutes — starving the connection pool and causing SQLITE_BUSY for
     * other writers. Each write is atomic on its own; the writes are idempotent and the episode is
     * retryable, and persisting the script before TTS is actually desirable so a TTS failure can be
     * resumed without losing the generated script.
     */
    suspend fun createEpisodeFromPipelineResult(
        podcast: Podcast,
        result: PipelineResult,
        generatingEpisode: Episode? = null,
        overrideGeneratedAt: String? = null,
        updateLastGenerated: Boolean = true
    ): Episode {
        val generatedAt = overrideGeneratedAt ?: generatingEpisode?.generatedAt ?: Instant.now().toString()
        val baseEpisode = generatingEpisode?.let {
            episodeRepository.findByIdOrNull(it.id!!) ?: it
        } ?: Episode(
            podcastId = podcast.id,
            generatedAt = generatedAt,
            scriptText = ""
        )

        val withScript = episodeRepository.save(
            baseEpisode.copy(
                generatedAt = generatedAt,
                scriptText = result.script,
                filterModel = result.filterModel,
                dedupModel = result.dedupModel,
                composeModel = result.composeModel,
                llmInputTokens = result.llmInputTokens,
                llmOutputTokens = result.llmOutputTokens,
                llmCostCents = result.llmCostCents,
                researchCalls = result.researchCalls,
                researchCostCents = result.researchCostCents,
                scoreInputTokens = result.scoreInputTokens,
                scoreOutputTokens = result.scoreOutputTokens,
                scoreCostCents = result.scoreCostCents,
                dedupInputTokens = result.dedupInputTokens,
                dedupOutputTokens = result.dedupOutputTokens,
                dedupCostCents = result.dedupCostCents,
                composeInputTokens = result.composeInputTokens,
                composeOutputTokens = result.composeOutputTokens,
                composeCostCents = result.composeCostCents,
                pipelineStage = if (podcast.requireReview) null else "tts",
                status = if (podcast.requireReview) EpisodeStatus.PENDING_REVIEW else EpisodeStatus.GENERATING,
                publishApproved = !podcast.requirePublishApproval
            )
        )

        val episode = if (podcast.requireReview) {
            withScript
        } else {
            ttsPipeline.generateForExistingEpisode(withScript, podcast).let {
                episodeRepository.save(it.copy(pipelineStage = null))
            }
        }

        saveEpisodeArticleLinks(episode, result)
        markArticlesAsProcessed(result.processedArticleIds)
        val recapEpisode = generateAndStoreRecap(episode, podcast, result.topicOrder)
        val finalEpisode = generateAndStoreShowNotes(recapEpisode)
        generateSourcesFile(finalEpisode, podcast)
        if (updateLastGenerated) {
            val freshPodcast = podcastRepository.findByIdOrNull(podcast.id)!!
            podcastRepository.save(freshPodcast.copy(lastGeneratedAt = Instant.now().toString()))
        }

        val eventName = if (podcast.requireReview) "episode.created" else "episode.generated"
        eventPublisher.publishEvent(
            PodcastEvent(this, podcast.id, "episode", finalEpisode.id!!, eventName,
                mapOf("episodeNumber" to finalEpisode.id))
        )

        return finalEpisode
    }

    private fun saveEpisodeArticleLinks(episode: Episode, result: PipelineResult) {
        val topicOrderMap = result.topicOrder.withIndex().associate { (index, label) -> label to index }
        for (articleId in result.processedArticleIds) {
            val topic = result.articleTopics[articleId]
            val topicOrder = topic?.let { topicOrderMap[it] }
            episodeArticleRepository.insertIgnore(episodeId = episode.id!!, articleId = articleId, topic = topic, topicOrder = topicOrder)
        }
    }

    private fun markArticlesAsProcessed(articleIds: List<Long>) {
        for (articleId in articleIds) {
            articleRepository.findByIdOrNull(articleId)?.let { article ->
                articleRepository.save(article.copy(isProcessed = true))
            }
        }
    }

    private fun generateAndStoreShowNotes(episode: Episode): Episode {
        val showNotes = episode.recap ?: return episode
        val updated = episodeRepository.save(episode.copy(showNotes = showNotes))
        log.info("[Pipeline] Show notes generated for episode {}", episode.id)
        return updated
    }

    private fun generateSourcesFile(episode: Episode, podcast: Podcast) {
        try {
            val articles = episodeArticleRepository.findArticlesWithTopicsByEpisodeId(episode.id!!)
            episodeSourcesGenerator.generate(episode, podcast, articles)
        } catch (e: Exception) {
            log.warn("Failed to generate sources.html for episode {}: {}", episode.id, e.message)
        }
    }

    private suspend fun generateAndStoreRecap(episode: Episode, podcast: Podcast, topicLabels: List<String> = emptyList()): Episode {
        return try {
            val filterModelDef = modelResolver.resolve(podcast, PipelineStage.FILTER)
            val recapResult = episodeRecapGenerator.generate(episode.scriptText, podcast, filterModelDef, topicLabels)
            val withStages = episode.copy(
                recap = recapResult.recap,
                recapInputTokens = recapResult.usage.inputTokens,
                recapOutputTokens = recapResult.usage.outputTokens,
                recapCostCents = recapResult.costCents ?: 0
            )
            val updated = episodeRepository.save(withStages.copy(
                llmInputTokens = withStages.sumStageInputTokens(),
                llmOutputTokens = withStages.sumStageOutputTokens(),
                llmCostCents = withStages.sumStageCostCents()
            ))
            log.info("[Pipeline] Recap generated and stored for episode {} (podcast '{}' ({}))", episode.id, podcast.name, podcast.id)
            // Demote topics the script did not actually discuss to "background" so the feed's
            // "Topics Covered" reflects the episode. Guard on non-empty: an empty set would clear
            // every topic_order (NOT IN () is true in SQLite).
            if (recapResult.coveredTopics.isNotEmpty()) {
                episodeArticleRepository.clearTopicOrderForUncoveredTopics(episode.id!!, recapResult.coveredTopics)
                log.info("[Pipeline] Episode {} covers {} of its candidate topics; others demoted to background", episode.id, recapResult.coveredTopics.size)
            }
            updated
        } catch (e: Exception) {
            log.warn("[Pipeline] Failed to generate recap for episode {} (podcast '{}' ({})) — continuing without recap: {} {}", episode.id, podcast.name, podcast.id, e.javaClass.simpleName, e.message)
            episode
        }
    }

    @Transactional
    fun saveDedupResults(episode: Episode, dedupResult: DedupStageResult) {
        val topicOrderMap = dedupResult.topicLabels.withIndex().associate { (index, label) -> label to index }
        for (fa in dedupResult.filteredArticles) {
            val topic = fa.topic
            val topicOrder = topic?.let { topicOrderMap[it] }
            episodeArticleRepository.insertIgnore(
                episodeId = episode.id!!,
                articleId = fa.article.id!!,
                topic = topic,
                topicOrder = topicOrder
            )
        }
        val fresh = episodeRepository.findByIdOrNull(episode.id!!) ?: episode
        val withStages = fresh.copy(
            filterModel = dedupResult.filterModel,
            dedupModel = dedupResult.dedupModel,
            scoreInputTokens = dedupResult.scoreInputTokens,
            scoreOutputTokens = dedupResult.scoreOutputTokens,
            scoreCostCents = dedupResult.scoreCostCents,
            dedupInputTokens = dedupResult.usage.inputTokens,
            dedupOutputTokens = dedupResult.usage.outputTokens,
            dedupCostCents = dedupResult.dedupCostCents ?: 0,
            composeInputTokens = 0,
            composeOutputTokens = 0,
            composeCostCents = 0,
            recapInputTokens = 0,
            recapOutputTokens = 0,
            recapCostCents = 0
        )
        episodeRepository.save(withStages.copy(
            llmInputTokens = withStages.sumStageInputTokens(),
            llmOutputTokens = withStages.sumStageOutputTokens(),
            llmCostCents = withStages.sumStageCostCents()
        ))
        log.info("[Pipeline] Saved dedup results for episode {} ({} articles)", episode.id, dedupResult.filteredArticles.size)
    }

    @Transactional
    fun saveComposeResult(episode: Episode, composeResult: ComposeStageResult) {
        val fresh = episodeRepository.findByIdOrNull(episode.id!!) ?: episode
        val withStages = fresh.copy(
            scriptText = composeResult.script,
            composeModel = composeResult.composeModel,
            composeInputTokens = composeResult.usage.inputTokens,
            composeOutputTokens = composeResult.usage.outputTokens,
            composeCostCents = composeResult.composeCostCents ?: 0,
            researchCalls = fresh.researchCalls + composeResult.researchCalls,
            researchCostCents = com.aisummarypodcast.llm.CostEstimator.addNullableCosts(fresh.researchCostCents, composeResult.researchCostCents)
        )
        episodeRepository.save(withStages.copy(
            llmInputTokens = withStages.sumStageInputTokens(),
            llmOutputTokens = withStages.sumStageOutputTokens(),
            llmCostCents = withStages.sumStageCostCents()
        ))
        log.info("[Pipeline] Saved compose result for episode {}", episode.id)
    }

    /**
     * NOT @Transactional: like [createEpisodeFromPipelineResult], this runs the slow TTS pipeline and
     * the recap LLM call between its writes. Holding a transaction (and SQLite write lock) across that
     * I/O starves the pool and causes SQLITE_BUSY. Each write is atomic and idempotent, and persisting
     * progress before TTS lets a failed episode resume rather than rolling back.
     */
    suspend fun finalizeEpisode(
        episode: Episode,
        podcast: Podcast,
        topicOrder: List<String> = emptyList(),
        updateLastGenerated: Boolean = true
    ): Episode {
        val fresh = episodeRepository.findByIdOrNull(episode.id!!) ?: episode

        // Publication approval is independent of the review gate: when required, the episode is
        // created un-approved and must be approved before it can be published.
        val publishApproved = !podcast.requirePublishApproval

        // Set status: PENDING_REVIEW or trigger TTS
        val withStatus = if (podcast.requireReview) {
            episodeRepository.save(fresh.copy(status = EpisodeStatus.PENDING_REVIEW, pipelineStage = null, publishApproved = publishApproved))
        } else {
            val generating = episodeRepository.save(fresh.copy(status = EpisodeStatus.GENERATING, pipelineStage = "tts", publishApproved = publishApproved))
            ttsPipeline.generateForExistingEpisode(generating, podcast).let {
                episodeRepository.save(it.copy(pipelineStage = null))
            }
        }

        // Mark articles as processed (idempotent: skip already-processed)
        val linkedArticles = episodeArticleRepository.findByEpisodeId(episode.id)
        val articleIds = linkedArticles.map { it.articleId }
        for (articleId in articleIds) {
            articleRepository.findByIdOrNull(articleId)?.let { article ->
                if (!article.isProcessed) {
                    articleRepository.save(article.copy(isProcessed = true))
                }
            }
        }

        // Generate recap (idempotent: skip if already exists)
        val recapEpisode = if (withStatus.recap != null) {
            withStatus
        } else {
            generateAndStoreRecap(withStatus, podcast, topicOrder)
        }

        val finalEpisode = generateAndStoreShowNotes(recapEpisode)
        generateSourcesFile(finalEpisode, podcast)

        if (updateLastGenerated) {
            val freshPodcast = podcastRepository.findByIdOrNull(podcast.id)!!
            podcastRepository.save(freshPodcast.copy(lastGeneratedAt = Instant.now().toString()))
        }

        val eventName = if (podcast.requireReview) "episode.created" else "episode.generated"
        eventPublisher.publishEvent(
            PodcastEvent(this, podcast.id, "episode", finalEpisode.id!!, eventName,
                mapOf("episodeNumber" to finalEpisode.id))
        )

        return finalEpisode
    }

    @Transactional
    fun resetForRetry(episode: Episode): Episode {
        val fresh = episodeRepository.findByIdOrNull(episode.id!!) ?: episode
        val reset = episodeRepository.save(
            fresh.copy(
                status = EpisodeStatus.GENERATING,
                errorMessage = null
            )
        )
        log.info("[Pipeline] Reset episode {} for retry", episode.id)
        return reset
    }

    @Transactional
    fun discardOnly(episode: Episode, podcastId: String) {
        val fresh = episodeRepository.findByIdOrNull(episode.id!!) ?: episode
        episodeRepository.save(fresh.copy(status = EpisodeStatus.DISCARDED))
        eventPublisher.publishEvent(
            PodcastEvent(this, podcastId, "episode", episode.id!!, "episode.discarded",
                mapOf("episodeNumber" to episode.id))
        )
        log.info("Episode {} discarded (no article reset)", episode.id)
    }

    @Transactional
    fun discardAndResetArticles(episode: Episode, podcastId: String) {
        val fresh = episodeRepository.findByIdOrNull(episode.id!!) ?: episode
        episodeRepository.save(fresh.copy(status = EpisodeStatus.DISCARDED))
        eventPublisher.publishEvent(
            PodcastEvent(this, podcastId, "episode", episode.id!!, "episode.discarded",
                mapOf("episodeNumber" to episode.id))
        )

        val linkedArticles = episodeArticleRepository.findByEpisodeId(episode.id!!)
        if (linkedArticles.isEmpty()) {
            log.warn("Episode {} has no episode-article links; cannot reset articles for reprocessing", episode.id)
            return
        }

        var resetCount = 0
        var deletedCount = 0
        var skippedCount = 0
        for (link in linkedArticles) {
            articleRepository.findByIdOrNull(link.articleId)?.let { article ->
                if (!articleEligibilityService.canResetArticle(article.id!!)) {
                    skippedCount++
                    return@let
                }
                val postCount = postArticleRepository.countByArticleId(article.id)
                if (postCount >= 2) {
                    postArticleRepository.deleteByArticleId(article.id)
                    articleRepository.deleteById(article.id)
                    deletedCount++
                } else {
                    articleRepository.save(article.copy(isProcessed = false))
                    resetCount++
                }
            }
        }

        // Roll back lastGeneratedAt so reset articles appear in upcoming again
        if (resetCount > 0) {
            val podcast = podcastRepository.findByIdOrNull(podcastId)
            if (podcast != null) {
                val lastPublished = episodeRepository.findLatestPublishedByPodcastId(podcastId)
                val rollbackTo = lastPublished?.generatedAt
                podcastRepository.save(podcast.copy(lastGeneratedAt = rollbackTo))
                log.info("Episode {} discard: rolled back lastGeneratedAt to {} for podcast '{}'", episode.id, rollbackTo ?: "null", podcast.name)
            }
        }

        log.info("Episode {} discarded, reset {} articles, deleted {} aggregated articles, skipped {} (linked to published episodes)", episode.id, resetCount, deletedCount, skippedCount)
    }

    fun updateScript(episode: Episode, scriptText: String): Episode {
        val fresh = episodeRepository.findByIdOrNull(episode.id!!) ?: episode
        return episodeRepository.save(fresh.copy(scriptText = scriptText))
    }

    fun approveAndGenerateAudio(episode: Episode, podcast: Podcast) {
        val fresh = episodeRepository.findByIdOrNull(episode.id!!) ?: episode
        episodeRepository.save(fresh.copy(status = EpisodeStatus.APPROVED, errorMessage = null))
        log.info("Episode {} approved, triggering async TTS generation", episode.id)
        eventPublisher.publishEvent(
            PodcastEvent(this, podcast.id, "episode", episode.id!!, "episode.approved",
                mapOf("episodeNumber" to episode.id))
        )
        audioGenerationService.generateAudioAsync(episode.id, podcast.id)
    }

    /**
     * Approves an episode for publication. Only meaningful when the podcast requires publication
     * approval; sets [Episode.publishApproved] so the Publish action is allowed.
     */
    fun approveForPublication(episode: Episode, podcast: Podcast): Episode {
        val fresh = episodeRepository.findByIdOrNull(episode.id!!) ?: episode
        val saved = episodeRepository.save(fresh.copy(publishApproved = true))
        log.info("Episode {} approved for publication", episode.id)
        eventPublisher.publishEvent(
            PodcastEvent(this, podcast.id, "episode", episode.id, "episode.publication_approved",
                mapOf("episodeNumber" to episode.id))
        )
        return saved
    }

    @Transactional
    fun failEpisode(podcast: Podcast, errorMessage: String, generatingEpisode: Episode? = null): Episode {
        val episode = if (generatingEpisode != null) {
            val fresh = episodeRepository.findByIdOrNull(generatingEpisode.id!!) ?: generatingEpisode
            episodeRepository.save(
                fresh.copy(
                    status = EpisodeStatus.FAILED,
                    errorMessage = errorMessage
                )
            )
        } else {
            episodeRepository.save(
                Episode(
                    podcastId = podcast.id,
                    generatedAt = Instant.now().toString(),
                    scriptText = "",
                    status = EpisodeStatus.FAILED,
                    errorMessage = errorMessage
                )
            )
        }
        val freshPodcast = podcastRepository.findByIdOrNull(podcast.id)!!
        podcastRepository.save(freshPodcast.copy(lastGeneratedAt = Instant.now().toString()))
        eventPublisher.publishEvent(
            PodcastEvent(this, podcast.id, "episode", episode.id!!, "episode.failed",
                mapOf("episodeNumber" to episode.id, "error" to errorMessage))
        )
        log.info("[Pipeline] Episode {} failed for podcast '{}' ({}): {}", episode.id, podcast.name, podcast.id, errorMessage)
        return episode
    }

    fun regenerateAudio(episode: Episode, podcast: Podcast) {
        log.info("Episode {} queued for audio regeneration", episode.id)
        audioGenerationService.regenerateAudioAsync(episode.id!!, podcast.id)
    }

    /**
     * NOT @Transactional: this method makes a slow LLM call (recap generation). A transaction here
     * would pin a JDBC connection (and a SQLite write lock) for the LLM's full duration, starving the
     * pool and risking SQLITE_BUSY. Each internal episode save is atomic on its own, and the recap →
     * show-notes → sources steps are independent idempotent updates that are safe to apply separately.
     */
    suspend fun regenerateRecap(episode: Episode, podcast: Podcast): Episode {
        // Derive the candidate topic labels from the episode's existing links (distinct topics that
        // currently carry a topic_order, in order) so re-running recap recomputes — and prunes — the
        // discussed set for already-generated episodes.
        val topicLabels = episodeArticleRepository.findByEpisodeId(episode.id!!)
            .filter { it.topicOrder != null && !it.topic.isNullOrBlank() }
            .sortedBy { it.topicOrder }
            .mapNotNull { it.topic }
            .distinct()
        val recapEpisode = generateAndStoreRecap(episode, podcast, topicLabels)
        val finalEpisode = generateAndStoreShowNotes(recapEpisode)
        generateSourcesFile(finalEpisode, podcast)
        return finalEpisode
    }

    fun findLinkedArticlesAndTopics(episodeId: Long): LinkedArticlesResult {
        val linkedArticles = episodeArticleRepository.findByEpisodeId(episodeId)
        val articles = linkedArticles.mapNotNull { link ->
            articleRepository.findByIdOrNull(link.articleId)
        }
        val topicLabels = linkedArticles
            .filter { it.topic != null && it.topicOrder != null }
            .sortedBy { it.topicOrder }
            .mapNotNull { it.topic }
            .distinct()
        val articleTopics = linkedArticles
            .filter { it.topic != null }
            .associate { it.articleId to it.topic!! }
        return LinkedArticlesResult(articles, topicLabels, articleTopics)
    }

    fun findArticlesForEpisode(episodeId: Long): List<EpisodeArticleResponse> {
        return episodeArticleRepository.findArticlesWithSourcesByEpisodeId(episodeId)
    }

    fun findRawArticlesForEpisode(episodeId: Long): List<Article> {
        return episodeArticleRepository.findRawArticlesByEpisodeId(episodeId)
    }

    fun findArticlesWithTopicsForEpisode(episodeId: Long): List<com.aisummarypodcast.store.TopicGroupedArticle> {
        return episodeArticleRepository.findArticlesWithTopicsByEpisodeId(episodeId)
    }

    @Transactional
    fun regenerateSourcesHtml(episodeId: Long): String? {
        val episode = episodeRepository.findByIdOrNull(episodeId) ?: return null
        val podcast = podcastRepository.findByIdOrNull(episode.podcastId) ?: return null
        val articles = episodeArticleRepository.findArticlesWithTopicsByEpisodeId(episodeId)
        val path = episodeSourcesGenerator.generate(episode, podcast, articles)
        return path?.toString()
    }

    /**
     * NOT @Transactional: this admin batch iterates every episode and generates source files (I/O).
     * A single transaction would pin one connection for the whole loop. Each per-episode save is
     * atomic, and the operation is idempotent, so per-episode independence is preferable to one
     * long-held connection.
     */
    fun regenerateAllShowNotes(): Map<String, Int> {
        val episodes = episodeRepository.findAll()
        var updatedShowNotes = 0
        var generatedSources = 0

        for (episode in episodes) {
            if (episode.recap != null && episode.showNotes != episode.recap) {
                episodeRepository.save(episode.copy(showNotes = episode.recap))
                updatedShowNotes++
            }

            val podcast = podcastRepository.findByIdOrNull(episode.podcastId) ?: continue
            try {
                generateSourcesFile(episode, podcast)
                generatedSources++
            } catch (e: Exception) {
                log.warn("Failed to generate sources.md for episode {}: {}", episode.id, e.message)
            }
        }

        log.info("Regenerated show notes for {} episodes, generated sources.md for {} episodes", updatedShowNotes, generatedSources)
        return mapOf(
            "updatedShowNotes" to updatedShowNotes,
            "generatedSources" to generatedSources,
            "totalEpisodes" to episodes.count()
        )
    }

    fun findById(episodeId: Long): Episode? = episodeRepository.findByIdOrNull(episodeId)

    fun countArticles(episodeId: Long): Int = episodeArticleRepository.findByEpisodeId(episodeId).size

    fun findByPodcastId(podcastId: String, status: EpisodeStatus? = null): List<Episode> {
        return if (status != null) {
            episodeRepository.findByPodcastIdAndStatusOrderByGeneratedAtDescIdDesc(podcastId, status)
        } else {
            episodeRepository.findByPodcastIdOrderByGeneratedAtDescIdDesc(podcastId)
        }
    }

    fun findByPodcastIdPaged(
        podcastId: String,
        statuses: Collection<EpisodeStatus>,
        pageable: org.springframework.data.domain.Pageable
    ): org.springframework.data.domain.Page<Episode> =
        if (statuses.isEmpty()) episodeRepository.findByPodcastId(podcastId, pageable)
        else episodeRepository.findByPodcastIdAndStatusIn(podcastId, statuses, pageable)

    fun hasActiveEpisode(podcastId: String): Boolean {
        return episodeRepository.findByPodcastIdAndStatusIn(
            podcastId,
            listOf(EpisodeStatus.GENERATING, EpisodeStatus.PENDING_REVIEW, EpisodeStatus.APPROVED, EpisodeStatus.GENERATING_AUDIO)
        ).isNotEmpty()
    }

    /**
     * Deletes episodes for [podcast] generated before [cutoff], removing both the MP3 file and the
     * database row. Returns the number of episodes removed. Owns the data access for the scheduled
     * cleanup so the scheduler stays an entry point (Rule A2).
     */
    @Transactional
    fun cleanupOldEpisodes(podcast: Podcast, cutoff: String): Int {
        val oldEpisodes = episodeRepository.findByPodcastId(podcast.id)
            .filter { it.generatedAt < cutoff }

        for (episode in oldEpisodes) {
            deleteAudioFile(episode)
            episodeRepository.delete(episode)
        }

        if (oldEpisodes.isNotEmpty()) {
            log.info("Cleaned up {} old episodes for podcast {}", oldEpisodes.size, podcast.id)
        }
        return oldEpisodes.size
    }

    private fun deleteAudioFile(episode: Episode) {
        val audioPath = episode.audioFilePath?.let { Path.of(it) } ?: return
        try {
            if (Files.exists(audioPath)) {
                Files.delete(audioPath)
            } else {
                log.warn("MP3 file not found for episode {}: {}", episode.id, audioPath)
            }
        } catch (e: Exception) {
            log.error("Failed to delete MP3 file for episode {}: {}", episode.id, e.message)
        }
    }

}
