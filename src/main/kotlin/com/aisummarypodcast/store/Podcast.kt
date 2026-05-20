package com.aisummarypodcast.store

import com.aisummarypodcast.config.LlmModelOverrides
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Table

@Table("podcasts")
data class Podcast(
    @Id val id: String,
    val userId: String,
    val name: String,
    val topic: String,
    val llmModels: LlmModelOverrides? = null,
    val language: String = "en",
    val ttsProvider: TtsProviderType = TtsProviderType.OPENAI,
    val ttsVoices: Map<String, String>? = null,
    val ttsSettings: Map<String, String>? = null,
    val style: PodcastStyle = PodcastStyle.NEWS_BRIEFING,
    val targetWords: Int? = null,
    val cron: String = "0 0 6 * * *",
    val timezone: String = "UTC",
    val customInstructions: String? = null,
    val relevanceThreshold: Int = 5,
    val requireReview: Boolean = false,
    val maxLlmCostCents: Int? = null,
    val maxArticleAgeDays: Int? = null,
    val speakerNames: Map<String, String>? = null,
    val fullBodyThreshold: Int? = null,
    val sponsor: Map<String, String>? = null,
    val pronunciations: Map<String, String>? = null,
    val recapLookbackEpisodes: Int? = null,
    val composeSettings: Map<String, String>? = null,
    val deepDiveEnabled: Boolean = false,
    val lastGeneratedAt: String? = null,
    @Version val version: Long? = null
)
