package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.LlmModelOverrides
import com.aisummarypodcast.config.ModelReference
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.Subtopics

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
    lastGeneratedAt = lastGeneratedAt
)

internal fun Episode.toResponse() = EpisodeResponse(
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
    researchCostCents = researchCostCents
)

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
