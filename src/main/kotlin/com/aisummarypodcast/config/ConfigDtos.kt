package com.aisummarypodcast.config

data class AvailableModel(
    val name: String,
    val type: String
)

data class PodcastDefaultsResponse(
    val llmModels: Map<String, ModelReference>,
    val availableModels: Map<String, List<AvailableModel>>,
    val maxLlmCostCents: Int,
    val targetWords: Int,
    val fullBodyThreshold: Int,
    val maxArticleAgeDays: Int,
    val research: ResearchDefaultsResponse
)

data class ResearchDefaultsResponse(
    val tavily: TavilyDefaultsResponse,
    val costBufferCents: Int
)

data class TavilyDefaultsResponse(
    val costPerCallCents: Int
)
