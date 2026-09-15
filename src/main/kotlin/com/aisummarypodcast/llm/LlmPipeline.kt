package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import io.github.resilience4j.kotlin.retry.executeSuspendFunction
import io.github.resilience4j.retry.RetryRegistry
import com.aisummarypodcast.source.SourceAggregator
import com.aisummarypodcast.podcast.EpisodeWindow
import com.aisummarypodcast.podcast.EpisodeWindowResolver
import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.ArticleRepository
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastStyle
import com.aisummarypodcast.store.PostRepository
import com.aisummarypodcast.store.SourceRepository
import com.aisummarypodcast.tts.TtsProviderFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlin.time.measureTimedValue

data class PipelineResult(
    val script: String,
    val filterModel: String,
    val composeModel: String,
    val llmInputTokens: Int = 0,
    val llmOutputTokens: Int = 0,
    val llmCostCents: Int? = null,
    val llmCostSource: LlmCostSource? = null,
    val processedArticleIds: List<Long> = emptyList(),
    val articleTopics: Map<Long, String> = emptyMap(),
    /**
     * Article id to the dedup follow-up context it was composed under. Persisted on the episode's
     * article links so a later regeneration recomposes with the same continuity annotations.
     */
    val followUpAnnotations: Map<Long, String> = emptyMap(),
    val dedupModel: String? = null,
    val topicOrder: List<String> = emptyList(),
    val researchCalls: Int = 0,
    val researchCostCents: Int? = null,
    val scoreInputTokens: Int = 0,
    val scoreOutputTokens: Int = 0,
    val scoreCostCents: Int = 0,
    val scoreReportedCostCents: Double? = null,
    val dedupInputTokens: Int = 0,
    val dedupOutputTokens: Int = 0,
    val dedupCostCents: Int = 0,
    val dedupReportedCostCents: Double? = null,
    val composeInputTokens: Int = 0,
    val composeOutputTokens: Int = 0,
    val composeCostCents: Int = 0,
    val composeReportedCostCents: Double? = null,
    /** Populated only for an evaluation run; see [EvaluationRunProvenance]. */
    val provenance: EvaluationRunProvenance? = null
)

data class PreviewResult(
    val script: String,
    val articleIds: List<Long>
)

data class DedupStageResult(
    val filteredArticles: List<FilteredArticle>,
    val filterModel: String,
    val dedupModel: String,
    val usage: TokenUsage,
    val followUpAnnotations: Map<Long, String>,
    val topicLabels: List<String>,
    val dedupCostCents: Int?,
    val dedupCostSource: LlmCostSource,
    val dedupReportedCostCents: Double? = null,
    val scoreInputTokens: Int = 0,
    val scoreOutputTokens: Int = 0,
    val scoreCostCents: Int = 0,
    val scoreCostSource: LlmCostSource = LlmCostSource.UNKNOWN,
    val scoreReportedCostCents: Double? = null
)

data class ComposeStageResult(
    val script: String,
    val composeModel: String,
    val usage: TokenUsage,
    val topicOrder: List<String>,
    val composeCostCents: Int?,
    val composeCostSource: LlmCostSource,
    val composeReportedCostCents: Double? = null,
    val researchCalls: Int = 0,
    val researchCostCents: Int? = null,
    /** Populated only for an evaluation run; see [EvaluationRunProvenance]. */
    val provenance: EvaluationRunProvenance? = null
)

@Component
class LlmPipeline(
    private val articleScoreSummarizer: ArticleScoreSummarizer,
    private val briefingComposer: BriefingComposer,
    private val dialogueComposer: DialogueComposer,
    private val interviewComposer: InterviewComposer,
    private val modelResolver: ModelResolver,
    private val articleRepository: ArticleRepository,
    private val sourceRepository: SourceRepository,
    private val postRepository: PostRepository,
    private val sourceAggregator: SourceAggregator,
    private val appProperties: AppProperties,
    private val ttsProviderFactory: TtsProviderFactory,
    private val articleEligibilityService: ArticleEligibilityService,
    private val topicDedupFilter: TopicDedupFilter,
    private val episodeWindowResolver: EpisodeWindowResolver,
    private val retryRegistry: RetryRegistry
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Aggregates, scores and selects the candidate articles for a run inside [window]. The window
     * is the run's input: it is decided and persisted by the caller before this is called, never
     * derived here.
     */
    suspend fun aggregateScoreAndFilter(
        podcast: Podcast,
        window: EpisodeWindow,
        onProgress: (stage: String, detail: Map<String, Any>) -> Unit = { _, _ -> }
    ): List<Article>? {
        val sources = sourceRepository.findByPodcastId(podcast.id)
        val sourceIds = sources.map { it.id }
        if (sourceIds.isEmpty()) {
            log.info("[LLM] Podcast '{}' ({}) has no sources — skipping", podcast.name, podcast.id)
            return null
        }

        val filterModelDef = modelResolver.resolve(podcast, PipelineStage.FILTER)
        val composeModelDef = modelResolver.resolve(podcast, PipelineStage.COMPOSE)
        val threshold = podcast.relevanceThreshold
        val sourceLabels = sources.associate { it.id to extractDomainAndPath(it.url) }

        // Step 1: Aggregate unlinked posts into articles
        val effectiveMaxArticleAgeDays = podcast.maxArticleAgeDays ?: appProperties.source.maxArticleAgeDays
        val cutoff = Instant.now().minus(effectiveMaxArticleAgeDays.toLong(), ChronoUnit.DAYS).toString()
        val unlinkedPosts = postRepository.findUnlinkedBySourceIds(sourceIds, cutoff)

        if (unlinkedPosts.isNotEmpty()) {
            onProgress("aggregating", mapOf("postCount" to unlinkedPosts.size))
            log.info("[LLM] Aggregating {} unlinked posts for podcast '{}' ({})", unlinkedPosts.size, podcast.name, podcast.id)
            val postsBySource = unlinkedPosts.groupBy { it.sourceId }
            for ((sourceId, posts) in postsBySource) {
                val source = sources.first { it.id == sourceId }
                sourceAggregator.aggregateAndPersist(posts, source)
            }
        }

        // Cost gate: estimate cost before any LLM calls
        val allUnscored = articleRepository.findUnscoredBySourceIds(sourceIds)
        if (allUnscored.isNotEmpty()) {
            val targetWords = podcast.targetWords ?: appProperties.briefing.targetWords
            val baseEstimate = CostEstimator.estimatePipelineCostCents(
                allUnscored, filterModelDef, composeModelDef, targetWords
            )
            val researchBuffer = if (podcast.deepDiveEnabled) appProperties.research.costBufferCents else 0
            val estimatedCostCents = baseEstimate?.let { it + researchBuffer }
            val costThreshold = podcast.maxLlmCostCents ?: appProperties.llm.maxCostCents
            if (estimatedCostCents == null) {
                log.warn("[LLM] Cost estimation unavailable for podcast '{}' ({}) — pricing not configured for model(s), skipping cost gate", podcast.name, podcast.id)
            } else if (estimatedCostCents > costThreshold) {
                log.warn("[LLM] Cost gate triggered for podcast '{}' ({}): estimated {}¢ exceeds threshold {}¢ — skipping pipeline", podcast.name, podcast.id, estimatedCostCents, costThreshold)
                return null
            } else {
                log.info("[LLM] Cost gate passed for podcast '{}' ({}): estimated {}¢ within threshold {}¢", podcast.name, podcast.id, estimatedCostCents, costThreshold)
            }
        }

        // Step 2: Score and summarize unscored articles
        val unscored = allUnscored
        if (unscored.isNotEmpty()) {
            onProgress("scoring", mapOf("articleCount" to unscored.size))
            log.info("[LLM] Scoring and summarizing {} articles for podcast '{}' ({})", unscored.size, podcast.name, podcast.id)
            val (scoredArticles, scoringDuration) = measureTimedValue {
                articleScoreSummarizer.scoreSummarize(unscored, podcast, filterModelDef, sourceLabels) { done, total ->
                    onProgress("scoring", mapOf("articleCount" to total, "scoredCount" to done))
                }
            }
            val relevantCount = scoredArticles.count { (it.relevanceScore ?: 0) >= threshold }
            log.info("[LLM] Score+summarize complete — {} articles in {} ({} relevant)", unscored.size, scoringDuration, relevantCount)
        }

        // Step 3: Find eligible articles
        val eligible = articleEligibilityService.findEligibleArticles(sourceIds, podcast, window)
        if (eligible.isEmpty()) {
            log.info("[LLM] No eligible articles for podcast '{}' ({}) — skipping briefing generation", podcast.name, podcast.id)
            return null
        }

        return eligible
    }

    /**
     * Eagerly aggregates and relevance-scores a podcast's non-aggregate sources (RSS, website,
     * YouTube — anything where [SourceAggregator.shouldAggregate] is false), so their articles are
     * ranked before any episode generation or preview. Aggregate sources (Twitter/nitter) are left
     * untouched: their posts merge into still-growing threads and must stay deferred to generation.
     *
     * Reuses the same aggregation and scoring components as the generation pipeline, so the work is
     * not repeated later (net-zero total cost). Respects the per-podcast LLM cost gate using a
     * scoring-only estimate; skips the podcast if its unscored ready-source articles would exceed it.
     */
    suspend fun scoreReadySources(podcast: Podcast) {
        val sources = sourceRepository.findByPodcastId(podcast.id)
        val readySources = sources.filter { !sourceAggregator.shouldAggregate(it) }
        if (readySources.isEmpty()) return
        val readyIds = readySources.map { it.id }

        // Step 1: aggregate unlinked posts (1:1 for non-aggregate sources)
        val effectiveMaxArticleAgeDays = podcast.maxArticleAgeDays ?: appProperties.source.maxArticleAgeDays
        val cutoff = Instant.now().minus(effectiveMaxArticleAgeDays.toLong(), ChronoUnit.DAYS).toString()
        val unlinkedPosts = postRepository.findUnlinkedBySourceIds(readyIds, cutoff)
        if (unlinkedPosts.isNotEmpty()) {
            val postsBySource = unlinkedPosts.groupBy { it.sourceId }
            for ((sourceId, posts) in postsBySource) {
                sourceAggregator.aggregateAndPersist(posts, readySources.first { it.id == sourceId })
            }
        }

        // Step 2: find unscored articles for these sources
        val unscored = articleRepository.findUnscoredBySourceIds(readyIds)
        if (unscored.isEmpty()) return

        // Step 3: cost gate (scoring-only estimate)
        val filterModelDef = modelResolver.resolve(podcast, PipelineStage.FILTER)
        val estimatedCostCents = CostEstimator.estimateScoringCostCents(unscored, filterModelDef)
        val costThreshold = podcast.maxLlmCostCents ?: appProperties.llm.maxCostCents
        if (estimatedCostCents != null && estimatedCostCents > costThreshold) {
            log.warn("[Eager] Cost gate skipped eager scoring for podcast '{}' ({}): estimated {}¢ exceeds threshold {}¢ — leaving {} articles for generation",
                podcast.name, podcast.id, estimatedCostCents, costThreshold, unscored.size)
            return
        }

        // Step 4: score (persists relevanceScore/summary/subtopic/tokens, same as generation)
        log.info("[Eager] Eagerly scoring {} ready-source articles for podcast '{}' ({})", unscored.size, podcast.name, podcast.id)
        val sourceLabels = readySources.associate { it.id to extractDomainAndPath(it.url) }
        articleScoreSummarizer.scoreSummarize(unscored, podcast, filterModelDef, sourceLabels)
    }

    suspend fun dedup(
        eligible: List<Article>,
        podcast: Podcast,
        onProgress: (stage: String, detail: Map<String, Any>) -> Unit = { _, _ -> }
    ): DedupStageResult? {
        val filterModelDef = modelResolver.resolve(podcast, PipelineStage.FILTER)
        val dedupModelDef = modelResolver.resolve(podcast, PipelineStage.DEDUP)

        onProgress("deduplicating", mapOf("articleCount" to eligible.size))

        val historicalArticles = articleEligibilityService.findHistoricalArticles(podcast)
        // Dedup retries internally (see TopicDedupFilter). If it still fails, we deliberately let the
        // exception propagate to fail the episode rather than silently composing un-deduped articles —
        // skipping dedup can produce a low-quality episode that repeats recently-covered topics.
        val dedupResult = topicDedupFilter.filter(eligible, historicalArticles, podcast.userId, dedupModelDef)

        if (dedupResult.filteredArticles.isEmpty()) {
            log.info("[LLM] All articles filtered as duplicates for podcast '{}' ({}) — skipping briefing generation", podcast.name, podcast.id)
            return null
        }

        // Cap the compose input to the highest-relevance articles. On busy days dozens can survive
        // scoring and dedup; composing all of them in one LLM call risks the compose timeout and
        // dilutes the episode. Cap here (as well as in compose) so the follow-up annotations, topic
        // labels, token totals, and episode-article links below all derive from the same capped set.
        val distinctArticles = distinctForCompose(dedupResult.filteredArticles, podcast)
        val composeArticles = capForCompose(distinctArticles)
        if (composeArticles.size < distinctArticles.size) {
            log.info("[LLM] Compose article cap applied for podcast '{}' ({}): {} → {} articles (top by relevance)",
                podcast.name, podcast.id, distinctArticles.size, composeArticles.size)
        }

        val followUpAnnotations = buildFollowUpAnnotations(composeArticles)
        val topicLabels = composeArticles.mapNotNull { it.topic }.distinct()
        val dedupCost = CostEstimator.resolveLlmCost(dedupResult.usage, dedupModelDef.cost)

        // Score-stage totals: sum tokens from the articles surviving into this episode. Where the
        // provider reported a cost per article those values are summed; the rest are estimated from
        // the SUM of their tokens (per-article integer cents lose sub-cent precision).
        val scoreInputTokens = composeArticles.sumOf { it.article.llmInputTokens ?: 0 }
        val scoreOutputTokens = composeArticles.sumOf { it.article.llmOutputTokens ?: 0 }
        val scoreCost = scoreStageCost(composeArticles.map { it.article }, filterModelDef)

        return DedupStageResult(
            filteredArticles = composeArticles,
            filterModel = filterModelDef.model,
            dedupModel = dedupModelDef.model,
            usage = dedupResult.usage,
            followUpAnnotations = followUpAnnotations,
            topicLabels = topicLabels,
            dedupCostCents = dedupCost.costCents?.roundToInt(),
            dedupCostSource = dedupCost.source,
            dedupReportedCostCents = dedupCost.reportedCostCents,
            scoreInputTokens = scoreInputTokens,
            scoreOutputTokens = scoreOutputTokens,
            scoreCostCents = scoreCost.costCents?.roundToInt() ?: 0,
            scoreCostSource = scoreCost.source,
            scoreReportedCostCents = scoreCost.reportedCostCents
        )
    }

    /**
     * Totals the score stage from the per-article calls: reported costs are summed and the articles
     * that reported nothing are estimated from their own tokens, so a partial sum is never
     * presented as a complete one (see [CostEstimator.aggregateStageCost]).
     */
    private fun scoreStageCost(articles: List<Article>, filterModelDef: ResolvedModel): ResolvedLlmCost =
        CostEstimator.aggregateStageCost(
            articles.map {
                LlmCallCost(it.llmInputTokens ?: 0, it.llmOutputTokens ?: 0, it.llmReportedCostUsd)
            },
            filterModelDef.cost
        )

    // Keeps the highest-relevance articles up to the configured compose cap; drops the rest.
    private fun capForCompose(articles: List<FilteredArticle>): List<FilteredArticle> =
        articles.sortedByDescending { it.article.relevanceScore ?: 0 }.take(appProperties.compose.maxArticles)

    /**
     * Drops repeated articles before the compose cap is applied. A repeat would otherwise consume a
     * slot of the cap, so a response that lists the same few articles many times can crowd out every
     * other topic and leave the composer with a fraction of the material it reports having.
     */
    private fun distinctForCompose(articles: List<FilteredArticle>, podcast: Podcast): List<FilteredArticle> {
        val distinct = articles.distinctBy { it.article.id }
        if (distinct.size < articles.size) {
            log.warn("[LLM] Dropped {} repeated article(s) for podcast '{}' ({}) — composing {} distinct",
                articles.size - distinct.size, podcast.name, podcast.id, distinct.size)
        }
        return distinct
    }

    /**
     * Composes the script for the articles selected for a run. The caller decides everything about
     * the prompt that is not derived from the articles themselves, including
     * [ComposeContext.episodeDate]: a retry, a re-run or a regeneration of a past day must state
     * that day rather than the day the run happens. The TTS guidelines are resolved here from the
     * podcast's provider and filled into the context.
     */
    suspend fun compose(
        filteredArticles: List<FilteredArticle>,
        podcast: Podcast,
        context: ComposeContext = ComposeContext(),
        onProgress: (stage: String, detail: Map<String, Any>) -> Unit = { _, _ -> }
    ): ComposeStageResult {
        val composeModelDef = modelResolver.resolve(podcast, PipelineStage.COMPOSE)
        // Enforce distinctness and the compose cap at this shared chokepoint so every entry path is
        // bounded — including retry-from-compose, which reloads previously persisted articles and
        // skips dedup entirely.
        val distinctArticles = distinctForCompose(filteredArticles, podcast)
        val composeArticles = capForCompose(distinctArticles)
        if (composeArticles.size < distinctArticles.size) {
            log.info("[LLM] Compose article cap applied for podcast '{}' ({}): {} → {} articles (top by relevance)",
                podcast.name, podcast.id, distinctArticles.size, composeArticles.size)
        }
        val toCompose = composeArticles.map { it.article }
        onProgress("composing", mapOf("articleCount" to toCompose.size))

        val ttsProvider = ttsProviderFactory.resolve(podcast)
        val composeContext = context.copy(
            ttsScriptGuidelines = ttsProvider.scriptGuidelines(podcast.style, podcast.pronunciations ?: emptyMap())
        )

        // Retried only on a transient provider fault (see the `compose` instance): an invalid or
        // incomplete completion, or an I/O failure. A speaker-tag failure must NOT land here —
        // RoleTagValidationAdvisor already re-issues the request up to twice inside the call, and
        // retrying the whole call would multiply the attempts and the cost of the pipeline's most
        // expensive stage. Before this, one `finish_reason is null` discarded ten minutes of
        // generation and failed the episode, while every cheaper stage around it retried.
        val compositionResult = retryRegistry.retry("compose").executeSuspendFunction {
            when (podcast.style) {
                PodcastStyle.DIALOGUE -> dialogueComposer.compose(toCompose, podcast, composeModelDef, composeContext)
                PodcastStyle.INTERVIEW -> interviewComposer.compose(toCompose, podcast, composeModelDef, composeContext)
                else -> briefingComposer.compose(toCompose, podcast, composeModelDef, composeContext)
            }
        }

        val composeCost = CostEstimator.resolveLlmCost(compositionResult.usage, composeModelDef.cost)

        val researchCostCents = if (compositionResult.researchCalls > 0) {
            compositionResult.researchCalls * appProperties.research.tavily.costPerCallCents
        } else null

        return ComposeStageResult(
            script = compositionResult.script,
            composeModel = composeModelDef.model,
            usage = compositionResult.usage,
            topicOrder = compositionResult.topicOrder,
            composeCostCents = composeCost.costCents?.roundToInt(),
            composeCostSource = composeCost.source,
            composeReportedCostCents = composeCost.reportedCostCents,
            researchCalls = compositionResult.researchCalls,
            researchCostCents = researchCostCents,
            provenance = compositionResult.provenance
        )
    }

    suspend fun run(podcast: Podcast, onProgress: (stage: String, detail: Map<String, Any>) -> Unit = { _, _ -> }): PipelineResult? {
        val window = episodeWindowResolver.resolveForNow(podcast)
        val eligible = aggregateScoreAndFilter(podcast, window, onProgress) ?: return null
        val dedupStageResult = dedup(eligible, podcast, onProgress) ?: return null
        val composeStageResult = compose(
            dedupStageResult.filteredArticles, podcast,
            ComposeContext(
                followUpAnnotations = dedupStageResult.followUpAnnotations,
                topicLabels = dedupStageResult.topicLabels,
                episodeDate = episodeWindowResolver.episodeDateOf(podcast, window)
            ),
            onProgress
        )

        val processedArticleIds = dedupStageResult.filteredArticles.map { it.article.id!! }
        val articleTopics = dedupStageResult.filteredArticles
            .filter { it.topic != null }
            .associate { it.article.id!! to it.topic!! }

        val totalCostCents = CostEstimator.addNullableCosts(dedupStageResult.dedupCostCents, composeStageResult.composeCostCents)

        log.info("[LLM] Pipeline complete for podcast '{}' ({}): {} articles processed into briefing", podcast.name, podcast.id, processedArticleIds.size)
        return PipelineResult(
            script = composeStageResult.script,
            filterModel = dedupStageResult.filterModel,
            composeModel = composeStageResult.composeModel,
            llmInputTokens = dedupStageResult.usage.inputTokens + composeStageResult.usage.inputTokens,
            llmOutputTokens = dedupStageResult.usage.outputTokens + composeStageResult.usage.outputTokens,
            llmCostCents = totalCostCents,
            llmCostSource = LlmCostSource.aggregate(
                listOf(dedupStageResult.scoreCostSource, dedupStageResult.dedupCostSource, composeStageResult.composeCostSource)
            ),
            processedArticleIds = processedArticleIds,
            articleTopics = articleTopics,
            followUpAnnotations = dedupStageResult.followUpAnnotations,
            dedupModel = dedupStageResult.dedupModel,
            topicOrder = composeStageResult.topicOrder,
            researchCalls = composeStageResult.researchCalls,
            researchCostCents = composeStageResult.researchCostCents,
            scoreInputTokens = dedupStageResult.scoreInputTokens,
            scoreOutputTokens = dedupStageResult.scoreOutputTokens,
            scoreCostCents = dedupStageResult.scoreCostCents,
            scoreReportedCostCents = dedupStageResult.scoreReportedCostCents,
            dedupInputTokens = dedupStageResult.usage.inputTokens,
            dedupOutputTokens = dedupStageResult.usage.outputTokens,
            dedupCostCents = dedupStageResult.dedupCostCents ?: 0,
            dedupReportedCostCents = dedupStageResult.dedupReportedCostCents,
            composeInputTokens = composeStageResult.usage.inputTokens,
            composeOutputTokens = composeStageResult.usage.outputTokens,
            composeCostCents = composeStageResult.composeCostCents ?: 0,
            composeReportedCostCents = composeStageResult.composeReportedCostCents,
            provenance = composeStageResult.provenance
        )
    }

    /**
     * Recomposes an episode from articles that were already selected and scored. The context's
     * [ComposeContext.episodeDate] is the day the source episode covered, not the day the
     * regeneration runs.
     */
    suspend fun recompose(
        articles: List<Article>,
        podcast: Podcast,
        context: ComposeContext = ComposeContext(),
        onProgress: (stage: String, detail: Map<String, Any>) -> Unit = { _, _ -> }
    ): PipelineResult {
        val composeModelDef = modelResolver.resolve(podcast, PipelineStage.COMPOSE)
        val ttsProvider = ttsProviderFactory.resolve(podcast)
        val composeContext = context.copy(
            ttsScriptGuidelines = ttsProvider.scriptGuidelines(podcast.style, podcast.pronunciations ?: emptyMap())
        )

        onProgress("composing", mapOf("articleCount" to articles.size))

        // Recompose runs no dedup stage, so the annotations must come from the source episode's
        // stored links. Without them the composer has no continuity signal and substitutes the
        // searchPastEpisodes tool, which demoted a launch story on an unrelated keyword match.
        val compositionResult = retryRegistry.retry("compose").executeSuspendFunction {
            when (podcast.style) {
                PodcastStyle.DIALOGUE -> dialogueComposer.compose(articles, podcast, composeModelDef, composeContext)
                PodcastStyle.INTERVIEW -> interviewComposer.compose(articles, podcast, composeModelDef, composeContext)
                else -> briefingComposer.compose(articles, podcast, composeModelDef, composeContext)
            }
        }

        val filterModelDef = modelResolver.resolve(podcast, PipelineStage.FILTER)
        val composeCost = CostEstimator.resolveLlmCost(compositionResult.usage, composeModelDef.cost)
        val costCents = composeCost.costCents?.roundToInt()

        val researchCostCents = if (compositionResult.researchCalls > 0) {
            compositionResult.researchCalls * appProperties.research.tavily.costPerCallCents
        } else null

        // Recompose reuses already-scored articles; score-stage totals are carried so the
        // Costs tab still shows the cost of scoring this episode's articles.
        val scoreInputTokens = articles.sumOf { it.llmInputTokens ?: 0 }
        val scoreOutputTokens = articles.sumOf { it.llmOutputTokens ?: 0 }
        val scoreCost = scoreStageCost(articles, filterModelDef)

        log.info("[LLM] Recompose complete for podcast '{}' ({}): {} articles", podcast.name, podcast.id, articles.size)
        return PipelineResult(
            script = compositionResult.script,
            filterModel = filterModelDef.model,
            composeModel = composeModelDef.model,
            llmInputTokens = compositionResult.usage.inputTokens,
            llmOutputTokens = compositionResult.usage.outputTokens,
            llmCostCents = costCents,
            llmCostSource = LlmCostSource.aggregate(listOf(scoreCost.source, composeCost.source)),
            processedArticleIds = articles.map { it.id!! },
            followUpAnnotations = context.followUpAnnotations,
            topicOrder = compositionResult.topicOrder,
            researchCalls = compositionResult.researchCalls,
            researchCostCents = researchCostCents,
            scoreInputTokens = scoreInputTokens,
            scoreOutputTokens = scoreOutputTokens,
            scoreCostCents = scoreCost.costCents?.roundToInt() ?: 0,
            scoreReportedCostCents = scoreCost.reportedCostCents,
            composeInputTokens = compositionResult.usage.inputTokens,
            composeOutputTokens = compositionResult.usage.outputTokens,
            composeCostCents = costCents ?: 0,
            composeReportedCostCents = composeCost.reportedCostCents,
            provenance = compositionResult.provenance
        )
    }

    suspend fun preview(podcast: Podcast, onProgress: (stage: String, detail: Map<String, Any>) -> Unit = { _, _ -> }): PreviewResult? {
        val sources = sourceRepository.findByPodcastId(podcast.id)
        val sourceIds = sources.map { it.id }
        if (sourceIds.isEmpty()) return null

        val filterModelDef = modelResolver.resolve(podcast, PipelineStage.FILTER)
        val composeModelDef = modelResolver.resolve(podcast, PipelineStage.COMPOSE)
        val sourceLabels = sources.associate { it.id to extractDomainAndPath(it.url) }

        // Step 1: Aggregate unlinked posts into articles
        val effectiveMaxArticleAgeDays = podcast.maxArticleAgeDays ?: appProperties.source.maxArticleAgeDays
        val cutoff = Instant.now().minus(effectiveMaxArticleAgeDays.toLong(), ChronoUnit.DAYS).toString()
        val unlinkedPosts = postRepository.findUnlinkedBySourceIds(sourceIds, cutoff)

        if (unlinkedPosts.isNotEmpty()) {
            onProgress("aggregating", mapOf("postCount" to unlinkedPosts.size))
            log.info("[LLM Preview] Aggregating {} unlinked posts for podcast '{}' ({})", unlinkedPosts.size, podcast.name, podcast.id)
            val postsBySource = unlinkedPosts.groupBy { it.sourceId }
            for ((sourceId, posts) in postsBySource) {
                val source = sources.first { it.id == sourceId }
                sourceAggregator.aggregateAndPersist(posts, source)
            }
        }

        // Step 2: Score unscored articles (persists scores)
        val unscored = articleRepository.findUnscoredBySourceIds(sourceIds)
        if (unscored.isNotEmpty()) {
            onProgress("scoring", mapOf("articleCount" to unscored.size))
            log.info("[LLM Preview] Scoring {} articles for podcast '{}' ({})", unscored.size, podcast.name, podcast.id)
            articleScoreSummarizer.scoreSummarize(unscored, podcast, filterModelDef, sourceLabels) { done, total ->
                onProgress("scoring", mapOf("articleCount" to total, "scoredCount" to done))
            }
        }

        // Step 3: Find eligible articles and run dedup filter
        val window = episodeWindowResolver.resolveForNow(podcast)
        val episodeDate = episodeWindowResolver.episodeDateOf(podcast, window)
        val eligible = articleEligibilityService.findEligibleArticles(sourceIds, podcast, window)
        if (eligible.isEmpty()) {
            log.info("[LLM Preview] No eligible articles for podcast '{}' ({})", podcast.name, podcast.id)
            return null
        }

        onProgress("deduplicating", mapOf("articleCount" to eligible.size))

        val dedupModelDef = modelResolver.resolve(podcast, PipelineStage.DEDUP)
        val historicalArticles = articleEligibilityService.findHistoricalArticles(podcast)
        // Let a dedup failure surface (the preview controller reports it as an error event) rather
        // than silently previewing un-deduped articles — consistent with the generation path.
        val dedupResult = topicDedupFilter.filter(eligible, historicalArticles, podcast.userId, dedupModelDef)

        if (dedupResult.filteredArticles.isEmpty()) {
            log.info("[LLM Preview] All articles filtered as duplicates for podcast '{}' ({})", podcast.name, podcast.id)
            return null
        }

        // Step 4: Compose script from filtered articles (NO marking as processed)
        val toCompose = dedupResult.filteredArticles.map { it.article }
        onProgress("composing", mapOf("articleCount" to toCompose.size))

        val ttsProvider = ttsProviderFactory.resolve(podcast)
        val followUpAnnotations = buildFollowUpAnnotations(dedupResult.filteredArticles)
        val composeContext = ComposeContext(
            ttsScriptGuidelines = ttsProvider.scriptGuidelines(podcast.style, podcast.pronunciations ?: emptyMap()),
            followUpAnnotations = followUpAnnotations,
            topicLabels = dedupResult.filteredArticles.mapNotNull { it.topic }.distinct(),
            episodeDate = episodeDate
        )

        // Same transient-fault retry as the compose stage above.
        val compositionResult = retryRegistry.retry("compose").executeSuspendFunction {
            when (podcast.style) {
                PodcastStyle.DIALOGUE -> dialogueComposer.compose(toCompose, podcast, composeModelDef, composeContext)
                PodcastStyle.INTERVIEW -> interviewComposer.compose(toCompose, podcast, composeModelDef, composeContext)
                else -> briefingComposer.compose(toCompose, podcast, composeModelDef, composeContext)
            }
        }

        log.info("[LLM Preview] Preview complete for podcast '{}' ({}): {} articles composed", podcast.name, podcast.id, toCompose.size)
        return PreviewResult(
            script = compositionResult.script,
            articleIds = toCompose.map { it.id!! }
        )
    }

    private fun buildFollowUpAnnotations(filteredArticles: List<FilteredArticle>): Map<Long, String> {
        val annotations = mutableMapOf<Long, String>()
        for (fa in filteredArticles) {
            if (fa.followUpContext != null && fa.article.id != null) {
                annotations[fa.article.id] = fa.followUpContext
            }
        }
        return annotations
    }
}
