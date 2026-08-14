## Why

Every LLM cost in the system is an estimate: tokens multiplied by per-model rates hand-maintained in `application.yaml`. OpenRouter already returns its authoritative charge inline in the usage object and we throw it away — `TokenUsage.fromChatResponse` keeps only `promptTokens` and `completionTokens` and discards the rest of the response metadata.

Verified against the live API through our own `ChatClientFactory`: the usage object carries `cost` (USD), `cost_details` (the upstream provider's prompt and completion split), and `is_byok`. Spring AI exposes them via `nativeUsage._additionalProperties()`, with `nativeUsage` being an `com.openai.models.completions.CompletionUsage`. No `usage: {include: true}` request flag is needed; OpenRouter returns them by default.

This matters beyond reporting accuracy. `LlmPipeline` aborts generation when the estimate exceeds `maxLlmCostCents`, so drift in the config table moves the threshold that decides whether an episode gets produced. The rates also need manual upkeep, which `CLAUDE.md` currently mandates ("always verify the pricing on the provider's website... it changes frequently").

## What Changes

- `TokenUsage` carries an optional provider-reported cost in USD alongside the token counts.
- The reported cost is read from `nativeUsage._additionalProperties()["cost"]` for live calls.
- `CostEstimator` prefers the reported cost and falls back to the configured per-Mtok rates, mirroring an `API` / `TABLE` / `UNKNOWN` resolution.
- Each episode records where its LLM cost came from, so an actual charge is distinguishable from an estimate.
- `llm_cache` stores the reported cost next to the token counts, and `CachingChatModel` replays it on a cache hit. This preserves today's semantics: the cache already replays token counts, so a cached call is already costed as if it ran, and the budget gate keeps behaving as it does now.
- A stage that makes several calls (scoring runs one per article) sums the costs that were reported and estimates the rest from their tokens, recording the stage as `MIXED` rather than presenting a partial sum as authoritative.
- The per-episode cost breakdown prefers a persisted reported cost over recomputing from tokens and rates.
- Migration `V64` adds the new columns, including a per-article reported cost so the score stage can be aggregated correctly.

Not breaking: no API contract is removed, the config rates stay in place as the fallback, and providers that report no cost (the direct `openai` provider) are unaffected.

## Capabilities

### New Capabilities

None. The behaviour belongs to the existing cost-tracking capability.

### Modified Capabilities

- `cost-tracking`: LLM cost is sourced from the provider's reported value when available, falling back to the configured rates; the source is recorded and surfaced; the API breakdown prefers a reported cost over recomputation from tokens.

## Impact

- `src/main/kotlin/com/aisummarypodcast/llm/TokenUsage.kt` — carries the reported cost, extracts it from native usage.
- `src/main/kotlin/com/aisummarypodcast/llm/CostEstimator.kt` — prefers reported over computed, returns the source.
- `src/main/kotlin/com/aisummarypodcast/llm/CachingChatModel.kt` — persists and replays the reported cost.
- `src/main/kotlin/com/aisummarypodcast/store/LlmCache.kt`, `Episode.kt`, `Article.kt` — new fields.
- `TokenUsage.plus` and `TokenUsage.ZERO` are removed as dead code rather than extended; aggregation belongs at the stage level.
- `src/main/kotlin/com/aisummarypodcast/llm/LlmPipeline.kt`, `ArticleScoreSummarizer.kt`, `EpisodeRecapGenerator.kt` — pass the reported cost through.
- `src/main/kotlin/com/aisummarypodcast/podcast/PodcastMappers.kt` — breakdown prefers reported cost.
- `src/main/resources/db/migration/V64__add_llm_cost_source.sql` — new migration (V63 is current).
- Only the `openrouter` provider reports cost; the direct `openai` provider continues on the configured rates.
- Historical episodes keep their estimated costs and are marked as such.
