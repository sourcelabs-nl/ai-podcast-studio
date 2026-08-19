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
typealias StageCostFn = (model: String?, inputTokens: Int, outputTokens: Int) -> Double?

fun findModelCost(modelName: String?, models: Map<String, Map<String, ModelCost>>): ModelCost? {
    if (modelName.isNullOrBlank()) return null
    return models.values.firstNotNullOfOrNull { it[modelName] }
}

fun stageCostFnFromModels(models: Map<String, Map<String, ModelCost>>): StageCostFn =
    { name, input, output ->
        findModelCost(name, models)?.let { CostEstimator.estimateLlmCostCentsExact(input, output, it) }
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
    scoreCalls: Int = 0,
    costFor: StageCostFn = noopStageCostFn
) = EpisodeResponse(
    id = id!!,
    podcastId = podcastId,
    generatedAt = generatedAt,
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
    costs = buildCosts(scoreCalls, costFor)
)

/**
 * Some sources store a whole post as the article title, so a match label can run to thousands of
 * characters. Cut it to something a list row can show.
 */
private const val MAX_MATCH_LABEL_LENGTH = 120

private fun String.ellipsize(): String {
    val collapsed = replace(Regex("\\s+"), " ").trim()
    return if (collapsed.length <= MAX_MATCH_LABEL_LENGTH) collapsed
    else collapsed.take(MAX_MATCH_LABEL_LENGTH).trimEnd() + "..."
}

/**
 * Maps a search hit, trimming the match lists to [EpisodeSearchService.MAX_MATCHES_PER_EPISODE].
 * The repository is asked for one more than the cap, so an over-long list is what reveals that
 * further matches exist.
 */
internal fun EpisodeSearchHit.toResponse(): EpisodeResponse {
    val cap = EpisodeSearchService.MAX_MATCHES_PER_EPISODE
    val hasMore = matches.topics.size > cap || matches.articleTitles.size > cap
    val topics = matches.topics.take(cap).map { it.ellipsize() }
    val titles = matches.articleTitles.take(cap).map { it.ellipsize() }
    return episode.toResponse().copy(
        matches = EpisodeMatchesResponse(
            topics = topics,
            articleTitles = titles,
            scriptOnly = topics.isEmpty() && titles.isEmpty(),
            hasMore = hasMore
        )
    )
}

private fun Episode.buildCosts(
    scoreCalls: Int,
    costFor: StageCostFn
): EpisodeCostsResponse {
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
    val composeCost = composeReportedCostCents
        ?: effective(composeCostCents, composeModel, composeInputTokens, composeOutputTokens)
    val recapCost = recapReportedCostCents
        ?: effective(recapCostCents, filterModel, recapInputTokens, recapOutputTokens)
    val ttsCost = (ttsCostCents ?: 0).toDouble()
    val researchCost = (researchCostCents ?: 0).toDouble()
    val totalCostCents = scoreCost + dedupCost + composeCost + recapCost + ttsCost + researchCost
    return EpisodeCostsResponse(
        score = LlmStageCostResponse(
            model = filterModel,
            calls = scoreCalls,
            inputTokens = scoreInputTokens,
            outputTokens = scoreOutputTokens,
            costCents = scoreCost
        ),
        dedup = LlmStageCostResponse(
            model = dedupModelLabel,
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
            calls = ttsCalls ?: 0,
            characters = ttsCharacters ?: 0,
            costCents = ttsCost
        ),
        research = ResearchCostResponse(
            calls = researchCalls,
            costCents = researchCost
        ),
        totalCostCents = totalCostCents,
        costSource = llmCostSource?.name
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
