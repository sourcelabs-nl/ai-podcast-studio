package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import io.github.resilience4j.kotlin.retry.executeSuspendFunction
import io.github.resilience4j.retry.RetryRegistry
import com.aisummarypodcast.source.SourceAggregator
import com.aisummarypodcast.podcast.EpisodeWindow
import com.aisummarypodcast.podcast.EpisodeWindowResolver
import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.ArticleRepository
import com.aisummarypodcast.store.CandidateOutcome
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastStyle
import com.aisummarypodcast.store.PostRepository
import com.aisummarypodcast.store.Source
import com.aisummarypodcast.store.SourceRepository
import com.aisummarypodcast.research.PreComposeResearch
import com.aisummarypodcast.research.PreComposeResearchService
import com.aisummarypodcast.research.ResearchRequest
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
    /**
     * Every article scored as a candidate for this episode, with what became of it. The episode
     * paid for scoring all of them, so it records all of them.
     */
    val candidates: List<EpisodeCandidate> = emptyList(),
    val scoreInputTokens: Int = 0,
    val scoreOutputTokens: Int = 0,
    val scoreCostCents: Int = 0,
    val scoreCostSource: LlmCostSource = LlmCostSource.UNKNOWN,
    val scoreReportedCostCents: Double? = null,
    /** The already-covered gate, costed apart from the clustering call it relieves. */
    val dedupGateInputTokens: Int = 0,
    val dedupGateCalls: Int = 0,
    val dedupGateCostCents: Int = 0,
    val dedupGateCostSource: LlmCostSource = LlmCostSource.UNKNOWN,
    val dedupGateReportedCostCents: Double? = null
)

/** An article that stood as a candidate for an episode, and what became of it. */
data class EpisodeCandidate(val articleId: Long, val outcome: CandidateOutcome)

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
    private val retryRegistry: RetryRegistry,
    private val preComposeResearchService: PreComposeResearchService
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
        episodeId: Long? = null,
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
        aggregateUnlinkedPosts(podcast, sources, onProgress)

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
                articleScoreSummarizer.scoreSummarize(
                    unscored, podcast, filterModelDef, ScoringContext(sourceLabels, episodeId)
                ) { done, total ->
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

    private fun aggregateUnlinkedPosts(
        podcast: Podcast,
        sources: List<Source>,
        onProgress: (stage: String, detail: Map<String, Any>) -> Unit
    ) {
        val sourceIds = sources.map { it.id }
        val effectiveMaxArticleAgeDays = podcast.maxArticleAgeDays ?: appProperties.source.maxArticleAgeDays
        val cutoff = Instant.now().minus(effectiveMaxArticleAgeDays.toLong(), ChronoUnit.DAYS).toString()
        val unlinkedPosts = postRepository.findUnlinkedBySourceIds(sourceIds, cutoff)
        if (unlinkedPosts.isEmpty()) return

        onProgress("aggregating", mapOf("postCount" to unlinkedPosts.size))
        log.info("[LLM] Aggregating {} unlinked posts for podcast '{}' ({})", unlinkedPosts.size, podcast.name, podcast.id)
        for ((sourceId, posts) in unlinkedPosts.groupBy { it.sourceId }) {
            sourceAggregator.aggregateAndPersist(posts, sources.first { it.id == sourceId })
        }
    }

    /**
     * Selects the articles for a focus episode inside [window]: every unused article of the window is
     * scored against [focus] (not the podcast's topic, and without persisting that score), and only
     * those clearing the podcast's relevance threshold are kept, capped like any compose input. No
     * dedup stage runs: the focus is the selection.
     *
     * @throws NoFocusRelevantArticlesException when nothing clears the threshold.
     */
    suspend fun selectForFocus(
        podcast: Podcast,
        window: EpisodeWindow,
        focus: String,
        episodeId: Long? = null,
        onProgress: (stage: String, detail: Map<String, Any>) -> Unit = { _, _ -> }
    ): FocusSelection {
        val sources = sourceRepository.findByPodcastId(podcast.id)
        val sourceIds = sources.map { it.id }
        if (sourceIds.isEmpty()) throw NoFocusRelevantArticlesException(focus)

        aggregateUnlinkedPosts(podcast, sources, onProgress)
        val candidates = articleEligibilityService.findEligibleArticlesForFocus(sourceIds, podcast, window)
        if (candidates.isEmpty()) throw NoFocusRelevantArticlesException(focus)

        val filterModelDef = modelResolver.resolve(podcast, PipelineStage.FILTER)
        val estimatedCostCents = CostEstimator.estimateScoringCostCents(candidates, filterModelDef)
        val costThreshold = podcast.maxLlmCostCents ?: appProperties.llm.maxCostCents
        if (estimatedCostCents != null && estimatedCostCents > costThreshold) {
            throw IllegalStateException(
                "Scoring ${candidates.size} articles for focus \"$focus\" is estimated at ${estimatedCostCents}¢, " +
                    "above the podcast's ${costThreshold}¢ cost threshold"
            )
        }

        val scored = scoreForFocus(podcast, candidates, focus, episodeId, onProgress)
        val relevant = scored.articles.filter { (it.article.relevanceScore ?: 0) >= podcast.relevanceThreshold }
        log.info("[LLM] Focus \"{}\" for podcast '{}' ({}): {} of {} candidates relevant",
            focus, podcast.name, podcast.id, relevant.size, candidates.size)
        if (relevant.isEmpty()) throw NoFocusRelevantArticlesException(focus)

        return scored.copy(articles = capForCompose(distinctForCompose(relevant, podcast)))
    }

    /**
     * Scores [articles] against [focus] and returns them as compose input with their focus relevance
     * and summary, without filtering. A feedback recompose of a focus episode calls this on its
     * locked article set to recover the focus summaries, which are never persisted; the LLM cache
     * makes that repeat pass a replay rather than a second charge.
     */
    suspend fun scoreForFocus(
        podcast: Podcast,
        articles: List<Article>,
        focus: String,
        episodeId: Long? = null,
        onProgress: (stage: String, detail: Map<String, Any>) -> Unit = { _, _ -> }
    ): FocusSelection {
        val sources = sourceRepository.findByPodcastId(podcast.id)
        val filterModelDef = modelResolver.resolve(podcast, PipelineStage.FILTER)
        onProgress("scoring", mapOf("articleCount" to articles.size))
        val scored = articleScoreSummarizer.scoreForFocus(
            articles, focus, podcast, filterModelDef,
            ScoringContext(sources.associate { it.id to extractDomainAndPath(it.url) }, episodeId)
        ) { done, total ->
            onProgress("scoring", mapOf("articleCount" to total, "scoredCount" to done))
        }
        val cost = CostEstimator.aggregateStageCost(
            scored.map { LlmCallCost(it.usage.inputTokens, it.usage.outputTokens, it.usage.reportedCostUsd) },
            filterModelDef.cost
        )
        return FocusSelection(
            articles = scored.map { FilteredArticle(it.article) },
            filterModel = filterModelDef.model,
            scoreInputTokens = scored.sumOf { it.usage.inputTokens },
            scoreOutputTokens = scored.sumOf { it.usage.outputTokens },
            scoreCostCents = cost.costCents?.roundToInt() ?: 0,
            scoreCostSource = cost.source,
            scoreReportedCostCents = cost.reportedCostCents
        )
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
        articleScoreSummarizer.scoreSummarize(unscored, podcast, filterModelDef, ScoringContext(sourceLabels))
    }

    suspend fun dedup(
        eligible: List<Article>,
        podcast: Podcast,
        episodeId: Long? = null,
        onProgress: (stage: String, detail: Map<String, Any>) -> Unit = { _, _ -> }
    ): DedupStageResult? {
        val filterModelDef = modelResolver.resolve(podcast, PipelineStage.FILTER)
        val dedupModelDef = modelResolver.resolve(podcast, PipelineStage.DEDUP)

        onProgress("deduplicating", mapOf("articleCount" to eligible.size))

        val history = articleEligibilityService.findHistory(podcast)
        // Dedup retries internally (see TopicDedupFilter). If it still fails, we deliberately let the
        // exception propagate to fail the episode rather than silently composing un-deduped articles —
        // skipping dedup can produce a low-quality episode that repeats recently-covered topics.
        val dedupResult = topicDedupFilter.filter(eligible, history, podcast.userId, dedupModelDef, episodeId)

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
        // The gate is costed on its own rather than added to the clustering call's total. It runs a
        // different model at different rates against a different question, and a charge folded into
        // the dedup amount cannot be told apart from the call it relieves.
        val gateCost = CostEstimator.resolveLlmCost(
            TokenUsage(dedupResult.gate.inputTokens, 0, dedupResult.gate.reportedCostUsd),
            null
        )

        // Score-stage totals: sum tokens across every article scored as a candidate for this
        // episode, not only those surviving into it. An article is scored against its full body,
        // and it is scored because it fell in this episode's window; charging the episode for the
        // survivors alone attributes the rest to nothing. Where the provider reported a cost per
        // article those values are summed; the rest are estimated from the SUM of their tokens
        // (per-article integer cents lose sub-cent precision).
        val candidates = candidateOutcomes(eligible, dedupResult.dropped, composeArticles)
        val scored = eligible.filter { it.id != null }
        val scoreInputTokens = scored.sumOf { it.llmInputTokens ?: 0 }
        val scoreOutputTokens = scored.sumOf { it.llmOutputTokens ?: 0 }
        val scoreCost = scoreStageCost(scored, filterModelDef)

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
            candidates = candidates,
            scoreInputTokens = scoreInputTokens,
            scoreOutputTokens = scoreOutputTokens,
            scoreCostCents = scoreCost.costCents?.roundToInt() ?: 0,
            scoreCostSource = scoreCost.source,
            scoreReportedCostCents = scoreCost.reportedCostCents,
            dedupGateInputTokens = dedupResult.gate.inputTokens,
            dedupGateCalls = dedupResult.gate.requests,
            dedupGateCostCents = gateCost.costCents?.roundToInt() ?: 0,
            dedupGateCostSource = gateCost.source,
            dedupGateReportedCostCents = gateCost.reportedCostCents
        )
    }

    /**
     * Every candidate of this run with what became of it: what the dedup stage dropped, what the
     * compose cap cut, and what reached the script.
     *
     * Built from [eligible] rather than from the dropped and used sets alone, so an article that
     * fell out of the run some other way is still recorded as a candidate the episode paid to
     * score.
     */
    private fun candidateOutcomes(
        eligible: List<Article>,
        dropped: List<DroppedCandidate>,
        composeArticles: List<FilteredArticle>
    ): List<EpisodeCandidate> {
        val droppedByDedup = dropped.associate { it.articleId to it.outcome }
        val usedIds = composeArticles.mapNotNull { it.article.id }.toSet()
        return eligible.mapNotNull { article ->
            val id = article.id ?: return@mapNotNull null
            val outcome = droppedByDedup[id]
                ?: if (id in usedIds) CandidateOutcome.USED else CandidateOutcome.CUT_BY_COMPOSE_CAP
            EpisodeCandidate(id, outcome)
        }
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
            ttsScriptGuidelines = ttsProvider.scriptGuidelines(podcast.style, podcast.pronunciations ?: emptyMap()),
            nextEpisodeDate = episodeWindowResolver.nextEpisodeDateAfter(podcast, context.episodeDate),
            research = research(toCompose, podcast, context)
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

        val researchCalls = composeContext.research.researchCalls
        val researchCostCents = researchCostCents(researchCalls)

        return ComposeStageResult(
            script = compositionResult.script,
            composeModel = composeModelDef.model,
            usage = compositionResult.usage,
            topicOrder = compositionResult.topicOrder,
            composeCostCents = composeCost.costCents?.roundToInt(),
            composeCostSource = composeCost.source,
            composeReportedCostCents = composeCost.reportedCostCents,
            researchCalls = researchCalls,
            researchCostCents = researchCostCents,
            provenance = compositionResult.provenance
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
        onProgress("composing", mapOf("articleCount" to articles.size))
        val composeContext = context.copy(
            ttsScriptGuidelines = ttsProvider.scriptGuidelines(podcast.style, podcast.pronunciations ?: emptyMap()),
            nextEpisodeDate = episodeWindowResolver.nextEpisodeDateAfter(podcast, context.episodeDate),
            research = research(articles, podcast, context)
        )

        // Recompose runs no dedup stage, so the annotations must come from the source episode's
        // stored links. Without them the composer has no continuity signal and leans on the
        // keyword-matched history block, which once demoted a launch story on an unrelated match.
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

        val researchCalls = composeContext.research.researchCalls
        val researchCostCents = researchCostCents(researchCalls)

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
            researchCalls = researchCalls,
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

    /**
     * The stages of a transient run (see [PipelineRunner]): aggregate, score, dedup and compose from
     * [window] without persisting an episode, and without marking any article processed.
     */
    suspend fun preview(
        podcast: Podcast,
        window: EpisodeWindow,
        onProgress: (stage: String, detail: Map<String, Any>) -> Unit = { _, _ -> }
    ): PreviewResult? {
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
            articleScoreSummarizer.scoreSummarize(
                unscored, podcast, filterModelDef, ScoringContext(sourceLabels)
            ) { done, total ->
                onProgress("scoring", mapOf("articleCount" to total, "scoredCount" to done))
            }
        }

        // Step 3: Find eligible articles and run dedup filter
        val episodeDate = episodeWindowResolver.episodeDateOf(podcast, window)
        val eligible = articleEligibilityService.findEligibleArticles(sourceIds, podcast, window)
        if (eligible.isEmpty()) {
            log.info("[LLM Preview] No eligible articles for podcast '{}' ({})", podcast.name, podcast.id)
            return null
        }

        onProgress("deduplicating", mapOf("articleCount" to eligible.size))

        val dedupModelDef = modelResolver.resolve(podcast, PipelineStage.DEDUP)
        val history = articleEligibilityService.findHistory(podcast)
        // Let a dedup failure surface (the preview controller reports it as an error event) rather
        // than silently previewing un-deduped articles — consistent with the generation path.
        val dedupResult = topicDedupFilter.filter(eligible, history, podcast.userId, dedupModelDef)

        if (dedupResult.filteredArticles.isEmpty()) {
            log.info("[LLM Preview] All articles filtered as duplicates for podcast '{}' ({})", podcast.name, podcast.id)
            return null
        }

        // Step 4: Compose script from filtered articles (NO marking as processed)
        val toCompose = dedupResult.filteredArticles.map { it.article }
        onProgress("composing", mapOf("articleCount" to toCompose.size))

        val ttsProvider = ttsProviderFactory.resolve(podcast)
        val followUpAnnotations = buildFollowUpAnnotations(dedupResult.filteredArticles)
        val previewContext = ComposeContext(
            ttsScriptGuidelines = ttsProvider.scriptGuidelines(podcast.style, podcast.pronunciations ?: emptyMap()),
            followUpAnnotations = followUpAnnotations,
            topicLabels = dedupResult.filteredArticles.mapNotNull { it.topic }.distinct(),
            episodeDate = episodeDate,
            nextEpisodeDate = episodeWindowResolver.nextEpisodeDateAfter(podcast, episodeDate)
        )
        val composeContext = previewContext.copy(research = research(toCompose, podcast, previewContext))

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

    /**
     * Runs the pre-compose research stage for a compose run. It sits outside the compose retry, so a
     * transient compose fault does not repeat the research. The subjects are the focus of a focus
     * episode followed by the topic clusters, or the article titles for a run that has neither.
     */
    private suspend fun research(articles: List<Article>, podcast: Podcast, context: ComposeContext): PreComposeResearch {
        val subjects = (listOfNotNull(context.focus) + context.topicLabels)
            .ifEmpty { articles.map { it.title } }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return preComposeResearchService.research(
            ResearchRequest(
                podcast = podcast,
                subjects = subjects,
                focusEpisode = context.focus != null,
                episodeId = context.episodeId
            )
        )
    }

    private fun researchCostCents(researchCalls: Int): Int? =
        if (researchCalls > 0) researchCalls * appProperties.research.tavily.costPerCallCents else null

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
