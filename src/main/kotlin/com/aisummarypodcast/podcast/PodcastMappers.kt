package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.LlmModelOverrides
import com.aisummarypodcast.config.ModelCost
import com.aisummarypodcast.config.ModelReference
import com.aisummarypodcast.llm.CostEstimator
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.Subtopics

/**
 * Lazy cost lookup used by [Episode.toResponse]. Returns the cost cents for the given
 * stage model + token counts, or null when the model has no pricing configured.
 * Used to fill in stage costs for legacy episodes that have tokens but were
 * persisted with `*_cost_cents = 0` (e.g. V57 backfilled score tokens without cost).
 */
typealias StageCostFn = (model: String?, inputTokens: Int, outputTokens: Int) -> Int?

fun findModelCost(modelName: String?, models: Map<String, Map<String, ModelCost>>): ModelCost? {
    if (modelName.isNullOrBlank()) return null
    return models.values.firstNotNullOfOrNull { it[modelName] }
}

fun stageCostFnFromModels(models: Map<String, Map<String, ModelCost>>): StageCostFn =
    { name, input, output ->
        findModelCost(name, models)?.let { CostEstimator.estimateLlmCostCents(input, output, it) }
    }

private val noopStageCostFn: StageCostFn = { _, _, _ -> null }

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
    requireReview = requireReview, maxLlmCostCents = maxLlmCostCents,
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

internal fun Episode.toResponse(scoreCalls: Int = 0, costFor: StageCostFn = noopStageCostFn) = EpisodeResponse(
    id = id!!,
    podcastId = podcastId,
    generatedAt = generatedAt,
    scriptText = scriptText,
    status = status.name,
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
    costs = buildCosts(scoreCalls, costFor)
)

private fun Episode.buildCosts(scoreCalls: Int, costFor: StageCostFn): EpisodeCostsResponse {
    fun llmCalls(input: Int, output: Int, cost: Int): Int =
        if (input > 0 || output > 0 || cost > 0) 1 else 0
    // Fall back to a runtime cost lookup when the persisted cost is 0 but we have tokens
    // (true for V57-backfilled scoring on legacy episodes, where SQL had no model rates).
    fun effective(persistedCost: Int, model: String?, input: Int, output: Int): Int =
        if (persistedCost > 0 || (input == 0 && output == 0)) persistedCost
        else costFor(model, input, output) ?: 0

    val scoreCost = effective(scoreCostCents, filterModel, scoreInputTokens, scoreOutputTokens)
    val dedupCost = effective(dedupCostCents, filterModel, dedupInputTokens, dedupOutputTokens)
    val composeCost = effective(composeCostCents, composeModel, composeInputTokens, composeOutputTokens)
    val recapCost = effective(recapCostCents, filterModel, recapInputTokens, recapOutputTokens)
    val totalCostCents = scoreCost + dedupCost + composeCost + recapCost +
        (ttsCostCents ?: 0) + (researchCostCents ?: 0)
    return EpisodeCostsResponse(
        score = LlmStageCostResponse(
            model = filterModel,
            calls = scoreCalls,
            inputTokens = scoreInputTokens,
            outputTokens = scoreOutputTokens,
            costCents = scoreCost
        ),
        dedup = LlmStageCostResponse(
            model = filterModel,
            calls = llmCalls(dedupInputTokens, dedupOutputTokens, dedupCost),
            inputTokens = dedupInputTokens,
            outputTokens = dedupOutputTokens,
            costCents = dedupCost
        ),
        compose = LlmStageCostResponse(
            model = composeModel,
            calls = llmCalls(composeInputTokens, composeOutputTokens, composeCost),
            inputTokens = composeInputTokens,
            outputTokens = composeOutputTokens,
            costCents = composeCost
        ),
        recap = LlmStageCostResponse(
            model = filterModel,
            calls = llmCalls(recapInputTokens, recapOutputTokens, recapCost),
            inputTokens = recapInputTokens,
            outputTokens = recapOutputTokens,
            costCents = recapCost
        ),
        tts = TtsCostResponse(
            model = ttsModel,
            characters = ttsCharacters ?: 0,
            costCents = ttsCostCents ?: 0
        ),
        research = ResearchCostResponse(
            calls = researchCalls,
            costCents = researchCostCents ?: 0
        ),
        totalCostCents = totalCostCents
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
        "postCount" to totalPostCount
    )
}
