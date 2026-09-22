package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.LlmModelOverrides
import com.aisummarypodcast.config.ModelCost
import com.aisummarypodcast.config.ModelReference
import com.aisummarypodcast.llm.CostEstimator
import com.aisummarypodcast.llm.LlmCostSource
import com.aisummarypodcast.store.CostStage
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeResearchSource
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.Subtopics

/**
 * Lazy cost lookup used by [Episode.toResponse]. Returns the cost cents for the given
 * stage model + token counts, or null when the model has no pricing configured.
 * Used to fill in stage costs for legacy episodes that have tokens but were
 * persisted with `*_cost_cents = 0` (e.g. V57 backfilled score tokens without cost).
 */
typealias StageCostFn = (model: String?, inputTokens: Int, outputTokens: Int) -> Double?

fun findModelCost(modelName: String?, models: Map<String, Map<String, ModelCost>>): ModelCost? {
    if (modelName.isNullOrBlank()) return null
    return models.values.firstNotNullOfOrNull { it[modelName] }
}

fun stageCostFnFromModels(models: Map<String, Map<String, ModelCost>>): StageCostFn =
    { name, input, output ->
        findModelCost(name, models)?.let { CostEstimator.estimateLlmCostCentsExact(input, output, it) }
    }

/** The lookup used where no rate table is in scope: every stage falls back to its persisted cost. */
internal val noopStageCostFn: StageCostFn = { _, _, _ -> null }

/** Requests are recorded in USD, as the provider reports them; the breakdown is served in cents. */
private const val USD_TO_CENTS = 100.0

/**
 * Nullable update helper: absent (null) keeps existing value, empty clears to null, non-empty updates.
 * Allows clearing nullable fields via the API by sending "" or {}.
 */
internal fun String?.orKeep(existing: String?): String? = when {
    this == null -> existing
    this.isEmpty() -> null
    else -> this
}

internal fun Map<String, String>?.orKeep(existing: Map<String, String>?): Map<String, String>? = when {
    this == null -> existing
    this.isEmpty() -> null
    else -> this
}

@JvmName("subtopicsOrKeep")
internal fun Map<String, Int>?.toSubtopics(existing: Subtopics?): Subtopics? = when {
    this == null -> existing
    this.isEmpty() -> null
    else -> Subtopics(this)
}

internal fun Map<String, ModelReference>?.toLlmModelOverrides(existing: LlmModelOverrides?): LlmModelOverrides? = when {
    this == null -> existing
    this.isEmpty() -> null
    else -> LlmModelOverrides(this)
}

internal fun Podcast.toResponse() = PodcastResponse(
    id = id, userId = userId, name = name, topic = topic,
    language = language, llmModels = llmModels?.stages, ttsProvider = ttsProvider.value, ttsVoices = ttsVoices,
    ttsSettings = ttsSettings,
    style = style.value, targetWords = targetWords, cron = cron, timezone = timezone,
    customInstructions = customInstructions, relevanceThreshold = relevanceThreshold,
    requireReview = requireReview, requirePublishApproval = requirePublishApproval, maxLlmCostCents = maxLlmCostCents,
    maxArticleAgeDays = maxArticleAgeDays, speakerNames = speakerNames,
    fullBodyThreshold = fullBodyThreshold, sponsor = sponsor, pronunciations = pronunciations,
    recapLookbackEpisodes = recapLookbackEpisodes, composeSettings = composeSettings,
    deepDiveEnabled = deepDiveEnabled,
    subtopics = subtopics?.weights,
    rapidFireWeightThreshold = rapidFireWeightThreshold,
    rapidFireMaxItems = rapidFireMaxItems,
    lastGeneratedAt = lastGeneratedAt
)

internal fun <T : Any> org.springframework.data.domain.Page<T>.toResponse(): PagedResponse<T> =
    PagedResponse(items = content, page = number, pageSize = size, total = totalElements, totalPages = totalPages)

internal fun <T : Any, R : Any> org.springframework.data.domain.Page<T>.toResponse(mapper: (T) -> R): PagedResponse<R> =
    PagedResponse(items = content.map(mapper), page = number, pageSize = size, total = totalElements, totalPages = totalPages)

internal fun Episode.toResponse(
    costContext: EpisodeCostContext = EpisodeCostContext()
) = EpisodeResponse(
    id = id!!,
    podcastId = podcastId,
    generatedAt = generatedAt,
    windowStart = windowStart,
    windowEnd = windowEnd,
    scriptText = scriptText,
    status = status.name,
    publishApproved = publishApproved,
    audioFilePath = audioFilePath,
    durationSeconds = durationSeconds,
    filterModel = filterModel,
    composeModel = composeModel,
    llmInputTokens = llmInputTokens,
    llmOutputTokens = llmOutputTokens,
    llmCostCents = llmCostCents,
    ttsCharacters = ttsCharacters,
    ttsCostCents = ttsCostCents,
    ttsModel = ttsModel,
    recap = recap,
    showNotes = showNotes,
    errorMessage = errorMessage,
    pipelineStage = pipelineStage,
    researchCalls = researchCalls,
    researchCostCents = researchCostCents,
    costs = buildCosts(costContext),
    focus = focus,
    reviewFeedback = reviewFeedback
)

internal fun EpisodeResearchSource.toResponse() = ResearchSourceResponse(query = query, title = title, url = url)

/**
 * Some sources store a whole post as the article title, so a match label can run to thousands of
 * characters. Cut it to something a list row can show on one line.
 */
private const val MAX_MATCH_LABEL_LENGTH = 80

private fun String.ellipsize(): String {
    val collapsed = replace(Regex("\\s+"), " ").trim()
    return if (collapsed.length <= MAX_MATCH_LABEL_LENGTH) collapsed
    else collapsed.take(MAX_MATCH_LABEL_LENGTH).trimEnd() + "..."
}

/**
 * Maps a search hit. The lists are capped for display while the totals count every match, so the
 * caller can summarise the remainder rather than implying the episode matched only what it shows.
 */
internal fun EpisodeSearchHit.toResponse(): EpisodeResponse {
    val cap = EpisodeSearchService.MAX_MATCHES_PER_EPISODE
    return episode.toResponse().copy(
        matches = EpisodeMatchesResponse(
            topics = matches.topics.take(cap).map { it.ellipsize() },
            articleTitles = matches.articleTitles.take(cap).map { it.ellipsize() },
            topicTotal = matches.topicTotal,
            articleTotal = matches.articleTotal,
            scriptOnly = matches.topicTotal == 0 && matches.articleTotal == 0,
            scriptContext = scriptContext
        )
    )
}

private fun Episode.buildCosts(context: EpisodeCostContext): EpisodeCostsResponse {
    val (scoreStage, costFor, dedupGateModel, projection) = context
    fun llmCalls(input: Int, output: Int, cost: Double): Int =
        if (input > 0 || output > 0 || cost > 0) 1 else 0
    // Always recompute the stage cost from tokens + model rate at full precision so sub-cent
    // costs (cheap models like deepseek-v4-flash) stay visible — the persisted integer-cent
    // value rounds them to 0. Fall back to the persisted value when no model rate is known
    // (e.g. legacy episodes whose model is no longer in config) or there are no tokens.
    fun effective(persistedCost: Int, model: String?, input: Int, output: Int): Double =
        if (input == 0 && output == 0) persistedCost.toDouble()
        else costFor(model, input, output) ?: persistedCost.toDouble()

    // A provider-reported cost is an actual charge, so it wins over recomputation from tokens and
    // rates. Each stage persists its own reported cents when a reported value contributed; a stage
    // with none (null) stays on the recompute-then-persisted fallback.
    val scoreCost = scoreReportedCostCents
        ?: effective(scoreCostCents, filterModel, scoreInputTokens, scoreOutputTokens)
    // Dedup runs on its own model; legacy episodes (null dedupModel) fall back to the filter model.
    val dedupModelLabel = dedupModel ?: filterModel
    val dedupCost = dedupReportedCostCents
        ?: effective(dedupCostCents, dedupModelLabel, dedupInputTokens, dedupOutputTokens)
    // The gate carries no model column of its own: it is configured, not resolved per podcast. The
    // configured model is named only for an episode whose gate actually issued requests, since for
    // an episode that ran without one it would name a model that never saw this script.
    val gateModelLabel = dedupGateModel?.takeIf { dedupGateCalls > 0 }
    val gateCost = dedupGateReportedCostCents
        ?: effective(dedupGateCostCents, null, dedupGateInputTokens, dedupGateOutputTokens)
    val composeCost = composeReportedCostCents
        ?: effective(composeCostCents, composeModel, composeInputTokens, composeOutputTokens)
    val recapCost = recapReportedCostCents
        ?: effective(recapCostCents, filterModel, recapInputTokens, recapOutputTokens)
    // Where the log accounts for a whole stage it is the answer, because it holds every request the
    // episode was charged for, including those of a run that paid and then failed. Where it does
    // not, the persisted column is: a projection over a partial log understates the stage.
    fun figures(stage: String, persisted: StageFigures): StageFigures =
        projection.stages[stage]?.let {
            StageFigures(
                calls = it.calls,
                inputTokens = it.inputTokens,
                outputTokens = it.outputTokens,
                // Summed in USD at full precision and converted once, so a stage of many sub-cent
                // requests does not round each of them away.
                costCents = (it.costUsd ?: 0.0) * USD_TO_CENTS
            )
        } ?: persisted

    val score = figures(
        CostStage.SCORE,
        StageFigures(scoreStage.calls, scoreInputTokens, scoreOutputTokens, scoreCost)
    )
    val dedup = figures(
        CostStage.DEDUP,
        StageFigures(llmCalls(dedupInputTokens, dedupOutputTokens, dedupCost), dedupInputTokens, dedupOutputTokens, dedupCost)
    )
    val gate = figures(
        CostStage.GATE,
        // Stored rather than derived from tokens: the gate chunks its candidates and retries
        // transient failures, so its request count does not follow from its size.
        StageFigures(dedupGateCalls, dedupGateInputTokens, dedupGateOutputTokens, gateCost)
    )
    val compose = figures(
        CostStage.COMPOSE,
        StageFigures(llmCalls(composeInputTokens, composeOutputTokens, composeCost), composeInputTokens, composeOutputTokens, composeCost)
    )
    val recap = figures(
        CostStage.RECAP,
        StageFigures(llmCalls(recapInputTokens, recapOutputTokens, recapCost), recapInputTokens, recapOutputTokens, recapCost)
    )

    val ttsCost = (ttsCostCents ?: 0).toDouble()
    val researchCost = (researchCostCents ?: 0).toDouble()
    // The score row's dropped breakdown is deliberately absent: that money is already inside the
    // score row, and adding it would charge the episode twice for the same requests.
    val totalCostCents = score.costCents + dedup.costCents + gate.costCents + compose.costCents +
        recap.costCents + ttsCost + researchCost

    // The source describes exactly the requests that were counted. A stage read from its column
    // contributes the source that column was written with, so a partly projected episode still
    // says what its total rests on.
    val projectedSources = projection.stages.values.flatMap { it.sources }
    val costSource =
        if (projection.stages.keys.containsAll(CostStage.ALL)) LlmCostSource.aggregate(projectedSources)
        else LlmCostSource.aggregate(projectedSources + listOfNotNull(llmCostSource))

    return EpisodeCostsResponse(
        score = LlmStageCostResponse(
            model = filterModel,
            calls = score.calls,
            inputTokens = score.inputTokens,
            outputTokens = score.outputTokens,
            costCents = score.costCents,
            droppedCalls = scoreStage.droppedCalls,
            droppedCostCents = scoreStage.droppedCostCents
        ),
        dedup = LlmStageCostResponse(
            model = dedupModelLabel,
            calls = dedup.calls,
            inputTokens = dedup.inputTokens,
            outputTokens = dedup.outputTokens,
            costCents = dedup.costCents
        ),
        dedupGate = LlmStageCostResponse(
            model = gateModelLabel,
            calls = gate.calls,
            inputTokens = gate.inputTokens,
            outputTokens = gate.outputTokens,
            costCents = gate.costCents
        ),
        compose = LlmStageCostResponse(
            model = composeModel,
            calls = compose.calls,
            inputTokens = compose.inputTokens,
            outputTokens = compose.outputTokens,
            costCents = compose.costCents
        ),
        recap = LlmStageCostResponse(
            model = filterModel,
            calls = recap.calls,
            inputTokens = recap.inputTokens,
            outputTokens = recap.outputTokens,
            costCents = recap.costCents
        ),
        tts = TtsCostResponse(
            model = ttsModel,
            calls = ttsCalls ?: 0,
            characters = ttsCharacters ?: 0,
            costCents = ttsCost
        ),
        research = ResearchCostResponse(
            calls = researchCalls,
            costCents = researchCost
        ),
        totalCostCents = totalCostCents,
        costSource = costSource?.name
    )
}

internal fun UpcomingContent.toResponse(): Map<String, Any> {
    val sourceMap = sources.associateBy { it.id }

    fun mapArticle(article: com.aisummarypodcast.store.Article) = EpisodeArticleResponse(
        id = article.id!!,
        title = article.title,
        url = article.url,
        author = article.author,
        publishedAt = article.publishedAt,
        relevanceScore = article.relevanceScore,
        summary = article.summary,
        body = article.body,
        subtopic = article.subtopic,
        postCount = postCounts[article.id] ?: 1,
        source = sourceMap[article.sourceId].let { source ->
            ArticleSourceResponse(
                id = source?.id ?: article.sourceId,
                type = source?.type?.name ?: "UNKNOWN",
                url = source?.url ?: "",
                label = source?.label
            )
        }
    )

    fun mapPost(post: com.aisummarypodcast.store.Post) = EpisodeArticleResponse(
        id = post.id!!,
        title = post.title,
        url = post.url,
        author = post.author,
        publishedAt = post.publishedAt,
        relevanceScore = null,
        summary = null,
        body = post.body,
        subtopic = null,
        source = sourceMap[post.sourceId].let { source ->
            ArticleSourceResponse(
                id = source?.id ?: post.sourceId,
                type = source?.type?.name ?: "UNKNOWN",
                url = source?.url ?: "",
                label = source?.label
            )
        }
    )

    val allArticles = articles.map(::mapArticle) + unlinkedPosts.map(::mapPost)

    return mapOf(
        "articles" to allArticles,
        "articleCount" to effectiveArticleCount,
        "postCount" to totalPostCount,
        "scoring" to LlmStageCostResponse(
            model = scoringSpend.model,
            calls = scoringSpend.calls,
            inputTokens = scoringSpend.inputTokens,
            outputTokens = scoringSpend.outputTokens,
            costCents = scoringSpend.costCents
        )
    )
}
