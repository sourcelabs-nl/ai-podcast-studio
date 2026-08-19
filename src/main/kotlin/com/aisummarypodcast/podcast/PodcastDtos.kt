package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.ModelReference
import com.fasterxml.jackson.annotation.JsonProperty

data class CreatePodcastRequest(
    val name: String,
    val topic: String,
    val language: String? = null,
    val llmModels: Map<String, ModelReference>? = null,
    val ttsProvider: String? = null,
    val ttsVoices: Map<String, String>? = null,
    val ttsSettings: Map<String, String>? = null,
    val style: String? = null,
    @JsonProperty("targetWords") val targetWords: Int? = null,
    val cron: String? = null,
    val timezone: String? = null,
    val customInstructions: String? = null,
    @JsonProperty("relevanceThreshold") val relevanceThreshold: Int? = null,
    @JsonProperty("requireReview") val requireReview: Boolean? = null,
    @JsonProperty("requirePublishApproval") val requirePublishApproval: Boolean? = null,
    @JsonProperty("maxLlmCostCents") val maxLlmCostCents: Int? = null,
    @JsonProperty("maxArticleAgeDays") val maxArticleAgeDays: Int? = null,
    val speakerNames: Map<String, String>? = null,
    @JsonProperty("fullBodyThreshold") val fullBodyThreshold: Int? = null,
    val sponsor: Map<String, String>? = null,
    val pronunciations: Map<String, String>? = null,
    @JsonProperty("recapLookbackEpisodes") val recapLookbackEpisodes: Int? = null,
    val composeSettings: Map<String, String>? = null,
    @JsonProperty("deepDiveEnabled") val deepDiveEnabled: Boolean? = null,
    val subtopics: Map<String, Int>? = null,
    @JsonProperty("rapidFireWeightThreshold") val rapidFireWeightThreshold: Int? = null,
    @JsonProperty("rapidFireMaxItems") val rapidFireMaxItems: Int? = null
)

data class UpdatePodcastRequest(
    val name: String,
    val topic: String,
    val language: String? = null,
    val llmModels: Map<String, ModelReference>? = null,
    val ttsProvider: String? = null,
    val ttsVoices: Map<String, String>? = null,
    val ttsSettings: Map<String, String>? = null,
    val style: String? = null,
    @JsonProperty("targetWords") val targetWords: Int? = null,
    val cron: String? = null,
    val timezone: String? = null,
    val customInstructions: String? = null,
    @JsonProperty("relevanceThreshold") val relevanceThreshold: Int? = null,
    @JsonProperty("requireReview") val requireReview: Boolean? = null,
    @JsonProperty("requirePublishApproval") val requirePublishApproval: Boolean? = null,
    @JsonProperty("maxLlmCostCents") val maxLlmCostCents: Int? = null,
    @JsonProperty("maxArticleAgeDays") val maxArticleAgeDays: Int? = null,
    val speakerNames: Map<String, String>? = null,
    @JsonProperty("fullBodyThreshold") val fullBodyThreshold: Int? = null,
    val sponsor: Map<String, String>? = null,
    val pronunciations: Map<String, String>? = null,
    @JsonProperty("recapLookbackEpisodes") val recapLookbackEpisodes: Int? = null,
    val composeSettings: Map<String, String>? = null,
    @JsonProperty("deepDiveEnabled") val deepDiveEnabled: Boolean? = null,
    val subtopics: Map<String, Int>? = null,
    @JsonProperty("rapidFireWeightThreshold") val rapidFireWeightThreshold: Int? = null,
    @JsonProperty("rapidFireMaxItems") val rapidFireMaxItems: Int? = null
)

data class PodcastResponse(
    val id: String,
    val userId: String,
    val name: String,
    val topic: String,
    val language: String,
    val llmModels: Map<String, ModelReference>?,
    val ttsProvider: String,
    val ttsVoices: Map<String, String>?,
    val ttsSettings: Map<String, String>?,
    val style: String,
    val targetWords: Int?,
    val cron: String,
    val timezone: String,
    val customInstructions: String?,
    val relevanceThreshold: Int,
    val requireReview: Boolean,
    val requirePublishApproval: Boolean,
    val maxLlmCostCents: Int?,
    val maxArticleAgeDays: Int?,
    val speakerNames: Map<String, String>?,
    val fullBodyThreshold: Int?,
    val sponsor: Map<String, String>?,
    val pronunciations: Map<String, String>?,
    val recapLookbackEpisodes: Int?,
    val composeSettings: Map<String, String>?,
    val deepDiveEnabled: Boolean,
    val subtopics: Map<String, Int>?,
    val rapidFireWeightThreshold: Int,
    val rapidFireMaxItems: Int?,
    val lastGeneratedAt: String?
)

data class EpisodeResponse(
    val id: Long,
    val podcastId: String,
    val generatedAt: String,
    val scriptText: String,
    val status: String,
    val publishApproved: Boolean,
    val audioFilePath: String?,
    val durationSeconds: Int?,
    val filterModel: String?,
    val composeModel: String?,
    val llmInputTokens: Int?,
    val llmOutputTokens: Int?,
    val llmCostCents: Int?,
    val ttsCharacters: Int?,
    val ttsCostCents: Int?,
    val ttsModel: String?,
    val recap: String?,
    val showNotes: String?,
    val errorMessage: String?,
    val pipelineStage: String?,
    val researchCalls: Int,
    val researchCostCents: Int?,
    val costs: EpisodeCostsResponse,
    /** Why this episode matched a search. Null when the request carried no search query. */
    val matches: EpisodeMatchesResponse? = null
)

/**
 * The covered topics and article titles that contain a query term, so the list can show why an
 * episode was returned. Both lists are capped; [hasMore] says the episode matched beyond them.
 */
data class EpisodeMatchesResponse(
    val topics: List<String>,
    val articleTitles: List<String>,
    /** Every matching topic, including those beyond the ones listed in [topics]. */
    val topicTotal: Int,
    /** Every matching article, including those beyond the ones listed in [articleTitles]. */
    val articleTotal: Int,
    /** True when the hit came only from the script, recap, or show notes. */
    val scriptOnly: Boolean,
    /** The spoken text around the keyword, when the episode's own text mentions it. */
    val scriptContext: String? = null
)

data class LlmStageCostResponse(
    val model: String?,
    val calls: Int,
    val inputTokens: Int,
    val outputTokens: Int,
    // Fractional cents so sub-cent stage costs from cheap models stay visible.
    val costCents: Double
)

data class TtsCostResponse(
    val model: String?,
    val calls: Int,
    val characters: Int,
    val costCents: Double
)

data class ResearchCostResponse(
    val calls: Int,
    val costCents: Double
)

data class EpisodeCostsResponse(
    val score: LlmStageCostResponse,
    val dedup: LlmStageCostResponse,
    val compose: LlmStageCostResponse,
    val recap: LlmStageCostResponse,
    val tts: TtsCostResponse,
    val research: ResearchCostResponse,
    val totalCostCents: Double,
    /**
     * Where the LLM cost came from: API, API_CACHED, TABLE, MIXED or UNKNOWN. Null for episodes
     * generated before the source was recorded; those are estimates.
     */
    val costSource: String?
)

data class UpdateScriptRequest(
    val scriptText: String
)

/**
 * Standard API envelope for paginated list endpoints. `page` is zero-indexed.
 * Mapped from Spring Data's `Page<T>` so the framework type never leaks to clients.
 */
data class PagedResponse<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val total: Long,
    val totalPages: Int
)

data class ArticleSourceResponse(
    val id: String,
    val type: String,
    val url: String,
    val label: String?
)

data class EpisodeArticleResponse(
    val id: Long,
    val title: String,
    val url: String,
    val author: String?,
    val publishedAt: String?,
    val relevanceScore: Int?,
    val summary: String?,
    val body: String?,
    val subtopic: String?,
    val source: ArticleSourceResponse
)
