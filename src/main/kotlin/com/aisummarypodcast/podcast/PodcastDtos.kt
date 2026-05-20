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
    @JsonProperty("maxLlmCostCents") val maxLlmCostCents: Int? = null,
    @JsonProperty("maxArticleAgeDays") val maxArticleAgeDays: Int? = null,
    val speakerNames: Map<String, String>? = null,
    @JsonProperty("fullBodyThreshold") val fullBodyThreshold: Int? = null,
    val sponsor: Map<String, String>? = null,
    val pronunciations: Map<String, String>? = null,
    @JsonProperty("recapLookbackEpisodes") val recapLookbackEpisodes: Int? = null,
    val composeSettings: Map<String, String>? = null,
    @JsonProperty("deepDiveEnabled") val deepDiveEnabled: Boolean? = null
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
    @JsonProperty("maxLlmCostCents") val maxLlmCostCents: Int? = null,
    @JsonProperty("maxArticleAgeDays") val maxArticleAgeDays: Int? = null,
    val speakerNames: Map<String, String>? = null,
    @JsonProperty("fullBodyThreshold") val fullBodyThreshold: Int? = null,
    val sponsor: Map<String, String>? = null,
    val pronunciations: Map<String, String>? = null,
    @JsonProperty("recapLookbackEpisodes") val recapLookbackEpisodes: Int? = null,
    val composeSettings: Map<String, String>? = null,
    @JsonProperty("deepDiveEnabled") val deepDiveEnabled: Boolean? = null
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
    val maxLlmCostCents: Int?,
    val maxArticleAgeDays: Int?,
    val speakerNames: Map<String, String>?,
    val fullBodyThreshold: Int?,
    val sponsor: Map<String, String>?,
    val pronunciations: Map<String, String>?,
    val recapLookbackEpisodes: Int?,
    val composeSettings: Map<String, String>?,
    val deepDiveEnabled: Boolean,
    val lastGeneratedAt: String?
)

data class EpisodeResponse(
    val id: Long,
    val podcastId: String,
    val generatedAt: String,
    val scriptText: String,
    val status: String,
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
    val researchCostCents: Int?
)

data class UpdateScriptRequest(
    val scriptText: String
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
    val source: ArticleSourceResponse
)
