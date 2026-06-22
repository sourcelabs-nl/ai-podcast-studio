## Context

The topic dedup stage clusters today's candidate articles against recent historical articles and decides what is new vs. already covered. It runs a single LLM call via `TopicDedupFilter` with a 8000-token output cap (`DEDUP_MAX_OUTPUT_TOKENS`) that guards against runaway/repetition responses. On failure, `LlmPipeline.dedup()` (and the preview path) silently fell back to composing all eligible articles un-deduped.

In production the dedup model for a podcast was `deepseek/deepseek-v4-pro`, a reasoning model. On OpenRouter, reasoning tokens count against `max_tokens`, so the model spent the entire 8000-token budget reasoning (≈140s), hit the cap, and returned empty content. `BeanOutputConverter` failed with "No content to map due to end-of-input"; after 3 in-filter retries the fallback kept all 53 articles undeduped. The historical context was also unbounded: every article (with full summary) from the last 7 GENERATED episodes — ~300 entries — which bloated the prompt and worsened the reasoning blow-up.

## Goals / Non-Goals

**Goals:**
- Make dedup succeed with a cheap, predictable model.
- Bound the dedup prompt size regardless of episode sizes.
- Stop silently producing un-deduped episodes; surface failures.

**Non-Goals:**
- Embedding/similarity-based historical pre-filtering (would require pgvector / a vector store; a future enhancement).
- Changing dedup's clustering logic, output schema, or follow-up annotation behavior.
- Adding a UI marker for "dedup skipped" (made moot by failing the episode instead).

## Decisions

- **Non-reasoning dedup model.** Default dedup model changed `anthropic/claude-sonnet-4.6` → `deepseek/deepseek-v4-flash`. Reasoning models are unsuitable: their reasoning tokens are charged against the output cap and can return empty completions. Spring AI 2.0.0's `OpenAiChatOptions` only exposes `reasoningEffort` (not OpenRouter's `reasoning: {enabled:false}`), so reliably disabling reasoning on a reasoning model is not possible — choosing a non-reasoning model sidesteps this entirely. Alternative considered: raise the token cap to fit reasoning + output (rejected: more cost, weaker runaway guard, still model-dependent).
- **Title-only, capped historical context.** Historical entries embed titles only (summaries dropped) and are capped to the most-recent `app.llm.dedup.max-historical-articles` (default 120). Titles convey the topic, which is all dedup needs for continuation detection. The cap bounds the prompt independent of how many articles each episode pulled in. Alternative considered: a dedicated dedup lookback decoupled from `recapLookbackEpisodes` (rejected as heavier; a count cap is simpler and directly bounds tokens).
- **Fail the episode on dedup failure.** Remove the silent fallback in `dedup()` and the preview path; let the exception propagate. The generate/retry flows already wrap the pipeline in try/catch → `failEpisode` (FAILED, retryable), and the preview controller emits an `error` SSE event. Retries remain handled inside `TopicDedupFilter` (3 attempts). Rationale: skipping dedup can yield a low-quality episode repeating recent topics; failing loudly is preferable to a silent bad episode.

## Risks / Trade-offs

- A cheaper model is weaker at clustering → bounding the prompt (title-only, cap) keeps the task tractable, and flash is already proven for the scoring stage.
- Failing the episode on a transient LLM hiccup → mitigated by the 3 in-filter retries and the existing retry/resume flow (episode is retryable from FULL_PIPELINE).
- A 120-article cap could drop older history for very large episodes → 120 ≈ several episodes of recall; tunable via config.

## Migration Plan

- No schema migration. Config-only default change plus code. The affected podcast's per-podcast dedup override was repointed to `deepseek/deepseek-v4-flash` via the podcast API.
- Rollback: revert the default model and restore the fallback try/catch blocks.
