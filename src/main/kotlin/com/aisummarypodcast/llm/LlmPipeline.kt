package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.source.SourceAggregator
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
import java.time.temporal.ChronoUnit
import kotlin.time.measureTimedValue

data class PipelineResult(
    val script: String,
    val filterModel: String,
    val composeModel: String,
    val llmInputTokens: Int = 0,
    val llmOutputTokens: Int = 0,
    val llmCostCents: Int? = null,
    val processedArticleIds: List<Long> = emptyList(),
    val articleTopics: Map<Long, String> = emptyMap(),
    val dedupModel: String? = null,
    val topicOrder: List<String> = emptyList(),
    val researchCalls: Int = 0,
    val researchCostCents: Int? = null,
    val scoreInputTokens: Int = 0,
    val scoreOutputTokens: Int = 0,
    val scoreCostCents: Int = 0,
    val dedupInputTokens: Int = 0,
    val dedupOutputTokens: Int = 0,
    val dedupCostCents: Int = 0,
    val composeInputTokens: Int = 0,
    val composeOutputTokens: Int = 0,
    val composeCostCents: Int = 0
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
    val scoreInputTokens: Int = 0,
    val scoreOutputTokens: Int = 0,
    val scoreCostCents: Int = 0
)

data class ComposeStageResult(
    val script: String,
    val composeModel: String,
    val usage: TokenUsage,
    val topicOrder: List<String>,
    val composeCostCents: Int?,
    val researchCalls: Int = 0,
    val researchCostCents: Int? = null
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
    private val topicDedupFilter: TopicDedupFilter
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun aggregateScoreAndFilter(
        podcast: Podcast,
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
        val eligible = articleEligibilityService.findEligibleArticles(sourceIds, podcast)
        if (eligible.isEmpty()) {
            log.info("[LLM] No eligible articles for podcast '{}' ({}) — skipping briefing generation", podcast.name, podcast.id)
            return null
        }

        return eligible
    }

    fun dedup(
        eligible: List<Article>,
        podcast: Podcast,
        onProgress: (stage: String, detail: Map<String, Any>) -> Unit = { _, _ -> }
    ): DedupStageResult? {
        val filterModelDef = modelResolver.resolve(podcast, PipelineStage.FILTER)
        val dedupModelDef = modelResolver.resolve(podcast, PipelineStage.DEDUP)

        onProgress("deduplicating", mapOf("articleCount" to eligible.size))

        val historicalArticles = articleEligibilityService.findHistoricalArticles(podcast)
        val dedupResult = try {
            topicDedupFilter.filter(eligible, historicalArticles, podcast.userId, dedupModelDef)
        } catch (e: Exception) {
            // A dedup failure (e.g. an unparseable LLM response) must not fail the whole episode.
            // Fall back to keeping all eligible articles — composition proceeds without dedup.
            log.warn("[LLM] Dedup failed for podcast '{}' ({}) — proceeding with all {} eligible articles (no dedup): {}",
                podcast.name, podcast.id, eligible.size, e.message)
            DedupFilterResult(eligible.map { FilteredArticle(it, null, null) }, TokenUsage(0, 0))
        }

        if (dedupResult.filteredArticles.isEmpty()) {
            log.info("[LLM] All articles filtered as duplicates for podcast '{}' ({}) — skipping briefing generation", podcast.name, podcast.id)
            return null
        }

        val followUpAnnotations = buildFollowUpAnnotations(dedupResult.filteredArticles)
        val topicLabels = dedupResult.filteredArticles.mapNotNull { it.topic }.distinct()
        val dedupCostCents = CostEstimator.estimateLlmCostCents(
            dedupResult.usage.inputTokens, dedupResult.usage.outputTokens, dedupModelDef.cost
        )

        // Score-stage totals: sum tokens from the articles surviving into this episode and
        // compute cost from the SUM (not per-article costs, which lose sub-cent precision).
        val scoreInputTokens = dedupResult.filteredArticles.sumOf { it.article.llmInputTokens ?: 0 }
        val scoreOutputTokens = dedupResult.filteredArticles.sumOf { it.article.llmOutputTokens ?: 0 }
        val scoreCostCents = CostEstimator.estimateLlmCostCents(
            scoreInputTokens, scoreOutputTokens, filterModelDef.cost
        ) ?: 0

        return DedupStageResult(
            filteredArticles = dedupResult.filteredArticles,
            filterModel = filterModelDef.model,
            dedupModel = dedupModelDef.model,
            usage = dedupResult.usage,
            followUpAnnotations = followUpAnnotations,
            topicLabels = topicLabels,
            dedupCostCents = dedupCostCents,
            scoreInputTokens = scoreInputTokens,
            scoreOutputTokens = scoreOutputTokens,
            scoreCostCents = scoreCostCents
        )
    }

    fun compose(
        filteredArticles: List<FilteredArticle>,
        podcast: Podcast,
        followUpAnnotations: Map<Long, String> = emptyMap(),
        topicLabels: List<String> = emptyList(),
        onProgress: (stage: String, detail: Map<String, Any>) -> Unit = { _, _ -> }
    ): ComposeStageResult {
        val composeModelDef = modelResolver.resolve(podcast, PipelineStage.COMPOSE)
        val toCompose = filteredArticles.map { it.article }
        onProgress("composing", mapOf("articleCount" to toCompose.size))

        val ttsProvider = ttsProviderFactory.resolve(podcast)
        val ttsScriptGuidelines = ttsProvider.scriptGuidelines(podcast.style, podcast.pronunciations ?: emptyMap())

        val compositionResult = when (podcast.style) {
            PodcastStyle.DIALOGUE -> dialogueComposer.compose(toCompose, podcast, composeModelDef, ttsScriptGuidelines, followUpAnnotations, topicLabels)
            PodcastStyle.INTERVIEW -> interviewComposer.compose(toCompose, podcast, composeModelDef, ttsScriptGuidelines, followUpAnnotations, topicLabels)
            else -> briefingComposer.compose(toCompose, podcast, composeModelDef, ttsScriptGuidelines, followUpAnnotations, topicLabels)
        }

        val composeCostCents = CostEstimator.estimateLlmCostCents(
            compositionResult.usage.inputTokens, compositionResult.usage.outputTokens, composeModelDef.cost
        )

        val researchCostCents = if (compositionResult.researchCalls > 0) {
            compositionResult.researchCalls * appProperties.research.tavily.costPerCallCents
        } else null

        return ComposeStageResult(
            script = compositionResult.script,
            composeModel = composeModelDef.model,
            usage = compositionResult.usage,
            topicOrder = compositionResult.topicOrder,
            composeCostCents = composeCostCents,
            researchCalls = compositionResult.researchCalls,
            researchCostCents = researchCostCents
        )
    }

    fun run(podcast: Podcast, onProgress: (stage: String, detail: Map<String, Any>) -> Unit = { _, _ -> }): PipelineResult? {
        val eligible = aggregateScoreAndFilter(podcast, onProgress) ?: return null
        val dedupStageResult = dedup(eligible, podcast, onProgress) ?: return null
        val composeStageResult = compose(
            dedupStageResult.filteredArticles, podcast,
            dedupStageResult.followUpAnnotations, dedupStageResult.topicLabels, onProgress
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
            processedArticleIds = processedArticleIds,
            articleTopics = articleTopics,
            dedupModel = dedupStageResult.dedupModel,
            topicOrder = composeStageResult.topicOrder,
            researchCalls = composeStageResult.researchCalls,
            researchCostCents = composeStageResult.researchCostCents,
            scoreInputTokens = dedupStageResult.scoreInputTokens,
            scoreOutputTokens = dedupStageResult.scoreOutputTokens,
            scoreCostCents = dedupStageResult.scoreCostCents,
            dedupInputTokens = dedupStageResult.usage.inputTokens,
            dedupOutputTokens = dedupStageResult.usage.outputTokens,
            dedupCostCents = dedupStageResult.dedupCostCents ?: 0,
            composeInputTokens = composeStageResult.usage.inputTokens,
            composeOutputTokens = composeStageResult.usage.outputTokens,
            composeCostCents = composeStageResult.composeCostCents ?: 0
        )
    }

    fun recompose(articles: List<Article>, podcast: Podcast, topicLabels: List<String> = emptyList(), onProgress: (stage: String, detail: Map<String, Any>) -> Unit = { _, _ -> }): PipelineResult {
        val composeModelDef = modelResolver.resolve(podcast, PipelineStage.COMPOSE)
        val ttsProvider = ttsProviderFactory.resolve(podcast)
        val ttsScriptGuidelines = ttsProvider.scriptGuidelines(podcast.style, podcast.pronunciations ?: emptyMap())

        onProgress("composing", mapOf("articleCount" to articles.size))

        val compositionResult = when (podcast.style) {
            PodcastStyle.DIALOGUE -> dialogueComposer.compose(articles, podcast, composeModelDef, ttsScriptGuidelines, topicLabels = topicLabels)
            PodcastStyle.INTERVIEW -> interviewComposer.compose(articles, podcast, composeModelDef, ttsScriptGuidelines, topicLabels = topicLabels)
            else -> briefingComposer.compose(articles, podcast, composeModelDef, ttsScriptGuidelines, topicLabels = topicLabels)
        }

        val filterModelDef = modelResolver.resolve(podcast, PipelineStage.FILTER)
        val costCents = CostEstimator.estimateLlmCostCents(
            compositionResult.usage.inputTokens, compositionResult.usage.outputTokens, composeModelDef.cost
        )

        val researchCostCents = if (compositionResult.researchCalls > 0) {
            compositionResult.researchCalls * appProperties.research.tavily.costPerCallCents
        } else null

        // Recompose reuses already-scored articles; score-stage totals are carried so the
        // Costs tab still shows the cost of scoring this episode's articles.
        val scoreInputTokens = articles.sumOf { it.llmInputTokens ?: 0 }
        val scoreOutputTokens = articles.sumOf { it.llmOutputTokens ?: 0 }
        val scoreCostCents = CostEstimator.estimateLlmCostCents(
            scoreInputTokens, scoreOutputTokens, filterModelDef.cost
        ) ?: 0

        log.info("[LLM] Recompose complete for podcast '{}' ({}): {} articles", podcast.name, podcast.id, articles.size)
        return PipelineResult(
            script = compositionResult.script,
            filterModel = filterModelDef.model,
            composeModel = composeModelDef.model,
            llmInputTokens = compositionResult.usage.inputTokens,
            llmOutputTokens = compositionResult.usage.outputTokens,
            llmCostCents = costCents,
            processedArticleIds = articles.map { it.id!! },
            topicOrder = compositionResult.topicOrder,
            researchCalls = compositionResult.researchCalls,
            researchCostCents = researchCostCents,
            scoreInputTokens = scoreInputTokens,
            scoreOutputTokens = scoreOutputTokens,
            scoreCostCents = scoreCostCents,
            composeInputTokens = compositionResult.usage.inputTokens,
            composeOutputTokens = compositionResult.usage.outputTokens,
            composeCostCents = costCents ?: 0
        )
    }

    fun preview(podcast: Podcast, onProgress: (stage: String, detail: Map<String, Any>) -> Unit = { _, _ -> }): PreviewResult? {
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
        val eligible = articleEligibilityService.findEligibleArticles(sourceIds, podcast)
        if (eligible.isEmpty()) {
            log.info("[LLM Preview] No eligible articles for podcast '{}' ({})", podcast.name, podcast.id)
            return null
        }

        onProgress("deduplicating", mapOf("articleCount" to eligible.size))

        val dedupModelDef = modelResolver.resolve(podcast, PipelineStage.DEDUP)
        val historicalArticles = articleEligibilityService.findHistoricalArticles(podcast)
        val dedupResult = try {
            topicDedupFilter.filter(eligible, historicalArticles, podcast.userId, dedupModelDef)
        } catch (e: Exception) {
            log.warn("[LLM Preview] Dedup failed for podcast '{}' ({}) — proceeding with all {} eligible articles (no dedup): {}",
                podcast.name, podcast.id, eligible.size, e.message)
            DedupFilterResult(eligible.map { FilteredArticle(it, null, null) }, TokenUsage(0, 0))
        }

        if (dedupResult.filteredArticles.isEmpty()) {
            log.info("[LLM Preview] All articles filtered as duplicates for podcast '{}' ({})", podcast.name, podcast.id)
            return null
        }

        // Step 4: Compose script from filtered articles (NO marking as processed)
        val toCompose = dedupResult.filteredArticles.map { it.article }
        onProgress("composing", mapOf("articleCount" to toCompose.size))

        val ttsProvider = ttsProviderFactory.resolve(podcast)
        val ttsScriptGuidelines = ttsProvider.scriptGuidelines(podcast.style, podcast.pronunciations ?: emptyMap())

        val followUpAnnotations = buildFollowUpAnnotations(dedupResult.filteredArticles)
        val topicLabels = dedupResult.filteredArticles.mapNotNull { it.topic }.distinct()

        val compositionResult = when (podcast.style) {
            PodcastStyle.DIALOGUE -> dialogueComposer.compose(toCompose, podcast, composeModelDef, ttsScriptGuidelines, followUpAnnotations, topicLabels)
            PodcastStyle.INTERVIEW -> interviewComposer.compose(toCompose, podcast, composeModelDef, ttsScriptGuidelines, followUpAnnotations, topicLabels)
            else -> briefingComposer.compose(toCompose, podcast, composeModelDef, ttsScriptGuidelines, followUpAnnotations, topicLabels)
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
