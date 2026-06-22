## Why

The topic dedup stage was silently failing and being skipped. The dedup model for at least one podcast was a reasoning model (`deepseek/deepseek-v4-pro`) whose reasoning tokens count against the 8000-token output cap, so it consumed the budget on reasoning and returned empty content. The pipeline caught the resulting parse error, fell back to "keep all articles, no dedup", and only logged a `WARN`. The episode was generated without deduplication and nothing surfaced in the UI or cost breakdown (it showed `$0 / 0 calls`, indistinguishable from "ran cheaply"). The oversized historical context (every article from the last 7 episodes, with full summaries, ~300 entries) compounded the problem by bloating the prompt.

## What Changes

- Switch the dedup stage default model from `anthropic/claude-sonnet-4.6` to `deepseek/deepseek-v4-flash` (a cheap, non-reasoning model). Reasoning models are unsuitable for dedup because their reasoning tokens are charged against the output cap and can yield empty completions.
- Bound the historical context sent to the dedup filter:
  - Embed only article **titles** in the historical block (drop summaries) — titles are sufficient for topic-recall.
  - Add `app.llm.dedup.max-historical-articles` (default 120) capping how many most-recent historical articles are embedded.
- **BREAKING (behavioral):** Remove the silent dedup fallback. A dedup failure that survives the existing in-filter retries now propagates and fails the episode generation (status `FAILED`, retryable) instead of composing un-deduped articles. The preview path surfaces the error too. Skipping dedup can produce a low-quality episode that repeats recently-covered topics, so failing loudly is preferred.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `article-dedup-filter`: historical context is now title-only and capped (was title + full summary, uncapped within the lookback); the dedup model must be non-reasoning; and a dedup failure now fails the episode rather than silently falling back to un-deduped composition.

## Impact

- Code: `LlmPipeline.kt` (remove fallback in `dedup()` and preview path), `TopicDedupFilter.kt` (title-only historical block), `ArticleEligibilityService.kt` (cap historical articles), `AppProperties.kt` (`DedupProperties`), `application.yaml` (dedup default model + `max-historical-articles`).
- Tests: `LlmPipelineTest`, `TopicDedupFilterTest`, `ArticleEligibilityServiceTest`.
- Data: the affected podcast's per-podcast dedup override was updated from `deepseek/deepseek-v4-pro` to `deepseek/deepseek-v4-flash` via the API.
- Behavior: episodes now fail on dedup error instead of completing without dedup.
