package com.aisummarypodcast.config

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/config")
class ConfigController(private val appProperties: AppProperties) {

    @GetMapping("/defaults")
    fun defaults(): PodcastDefaultsResponse {
        val llmModels = mapOf(
            "filter" to appProperties.llm.defaults.filter,
            "dedup" to appProperties.llm.defaults.dedup,
            "compose" to appProperties.llm.defaults.compose
        )

        return PodcastDefaultsResponse(
            llmModels = llmModels,
            availableModels = appProperties.models.toSelectableModels(),
            maxLlmCostCents = appProperties.llm.maxCostCents,
            targetWords = appProperties.briefing.targetWords,
            fullBodyThreshold = appProperties.briefing.fullBodyThreshold,
            maxArticleAgeDays = appProperties.source.maxArticleAgeDays,
            research = ResearchDefaultsResponse(
                tavily = TavilyDefaultsResponse(
                    costPerCallCents = appProperties.research.tavily.costPerCallCents
                ),
                costBufferCents = appProperties.research.costBufferCents
            )
        )
    }
}
