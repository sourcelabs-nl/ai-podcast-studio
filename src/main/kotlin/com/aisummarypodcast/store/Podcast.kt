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
    val cron: String = DEFAULT_CRON,
    val timezone: String = DEFAULT_TIMEZONE,
    val customInstructions: String? = null,
    val relevanceThreshold: Int = DEFAULT_RELEVANCE_THRESHOLD,
    val requireReview: Boolean = false,
    val requirePublishApproval: Boolean = false,
    val maxLlmCostCents: Int? = null,
    val maxArticleAgeDays: Int? = null,
    val speakerNames: Map<String, String>? = null,
    val fullBodyThreshold: Int? = null,
    val sponsor: Map<String, String>? = null,
    val pronunciations: Map<String, String>? = null,
    val recapLookbackEpisodes: Int? = null,
    val composeSettings: Map<String, String>? = null,
    val deepDiveEnabled: Boolean = false,
    val subtopics: Subtopics? = null,
    val rapidFireWeightThreshold: Int = DEFAULT_RAPID_FIRE_WEIGHT_THRESHOLD,
    val rapidFireMaxItems: Int? = null,
    val lastGeneratedAt: String? = null,
    @Version val version: Long? = null
) {
    companion object {
        const val DEFAULT_CRON = "0 0 6 * * *"
        const val DEFAULT_TIMEZONE = "UTC"
        const val DEFAULT_RELEVANCE_THRESHOLD = 5
        const val DEFAULT_RAPID_FIRE_WEIGHT_THRESHOLD = 3
    }
}
