package com.aisummarypodcast.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val llm: LlmProperties,
    val briefing: BriefingProperties,
    val episodes: EpisodesProperties,
    val feed: FeedProperties,
    val encryption: EncryptionProperties,
    val models: Map<String, Map<String, ModelCost>> = emptyMap(),
    val llmCache: LlmCacheProperties = LlmCacheProperties(),
    val source: SourceProperties = SourceProperties(),
    val soundcloud: SoundCloudProperties = SoundCloudProperties(),
    val x: XProperties = XProperties(),
    val episode: EpisodeProperties = EpisodeProperties(),
    val research: ResearchProperties = ResearchProperties(),
    val compose: ComposeProperties = ComposeProperties(),
    val backup: BackupProperties = BackupProperties()
)

/**
 * Scheduled SQLite backup configuration. [directory] is a fixed filesystem path (not runtime-editable);
 * [enabled], [cron], and [retentionCount] only seed the persisted `backup_settings` row on first run —
 * the database row is the runtime source of truth and is edited from the settings page.
 */
data class BackupProperties(
    val enabled: Boolean = true,
    val cron: String = "0 0 2 * * *",
    val directory: String = "./data/backups",
    val retentionCount: Int = 7
)

data class ComposeProperties(
    val rapidFireBudgetFraction: Double = 0.15,
    val rapidFireMaxItems: Int = 6
)

data class ResearchProperties(
    val tavily: TavilyProperties = TavilyProperties(),
    val costBufferCents: Int = 5
)

data class TavilyProperties(
    val costPerCallCents: Int = 1
)

data class EncryptionProperties(
    val masterKey: String
)

data class LlmProperties(
    val defaults: StageDefaults = StageDefaults(),
    val maxCostCents: Int = 200,
    val scoring: ScoringProperties = ScoringProperties()
)

data class ScoringProperties(
    val concurrency: Int = 10,
    val maxRetries: Int = 3
)

enum class ModelType { LLM, TTS }

data class ModelCost(
    val type: ModelType,
    val inputCostPerMtok: Double? = null,
    val outputCostPerMtok: Double? = null,
    val costPerMillionChars: Double? = null
)

data class ModelReference(
    val provider: String,
    val model: String
)

data class LlmModelOverrides(
    val stages: Map<String, ModelReference> = emptyMap()
) {
    operator fun get(key: String): ModelReference? = stages[key]
    fun isEmpty(): Boolean = stages.isEmpty()
}

data class StageDefaults(
    val filter: ModelReference = ModelReference("openrouter", "openai/gpt-5.4-nano"),
    val compose: ModelReference = ModelReference("openrouter", "anthropic/claude-sonnet-4.6")
)

data class BriefingProperties(
    val targetWords: Int = 1500,
    val fullBodyThreshold: Int = 5,
    val defaultTemperature: Double = 0.95
)

data class EpisodesProperties(
    val directory: String = "./data",
    val retentionDays: Int = 30
)

data class FeedProperties(
    val baseUrl: String = "http://localhost:8085",
    val title: String = "AI Summary Podcast",
    val description: String = "AI-generated audio briefings from your favourite content sources",
    val staticBaseUrl: String? = null,
    val ownerName: String? = null,
    val ownerEmail: String? = null,
    val author: String? = null,
    val itunesCategory: String = "Technology",
    val explicit: Boolean = false
)

data class LlmCacheProperties(
    val maxAgeDays: Int? = null
)

data class SourceProperties(
    val maxArticleAgeDays: Int = 7,
    val maxFailures: Int = 15,
    val maxBackoffHours: Int = 24,
    // If the last completed poll round is older than this (or none has completed yet), the briefing
    // generator runs a catch-up poll before composing — guards against generating on stale data
    // after the machine was asleep/offline through the scheduled time.
    val staleRoundThresholdMinutes: Int = 10,
    val pollDelaySeconds: Map<String, Int> = emptyMap(),
    val hostOverrides: Map<String, HostOverride> = emptyMap(),
    val deepFetch: DeepFetchProperties = DeepFetchProperties()
)

/**
 * Controls "deep-fetching" the full article behind an RSS link. When [enabled], the RSS fetcher
 * retrieves each entry's linked page and prefers its extracted text over the (often summary-only)
 * feed body. Hosts matching any entry in [skipHosts] are left on their feed text, since their
 * links are not scrapeable articles (e.g. Twitter/X and its mirrors, YouTube watch pages).
 */
data class DeepFetchProperties(
    val enabled: Boolean = true,
    val timeoutMs: Int = 15_000,
    val skipHosts: List<String> = listOf("x.com", "twitter.com", "nitter", "youtube.com", "youtu.be")
)

data class HostOverride(
    val pollDelaySeconds: Int = 0
)


data class SoundCloudProperties(
    val clientId: String? = null,
    val clientSecret: String? = null
)

data class XProperties(
    val clientId: String? = null,
    val clientSecret: String? = null
)

data class EpisodeProperties(
    val recapLookbackEpisodes: Int = 7
)
