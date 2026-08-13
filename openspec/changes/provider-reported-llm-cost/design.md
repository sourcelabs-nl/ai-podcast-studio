## Context

LLM spend is currently derived entirely from `CostEstimator.estimateLlmCostCentsExact(inputTokens, outputTokens, cost)`, where `cost` comes from `app.models.<provider>.<model>` in `application.yaml`. The only input is `TokenUsage`, which `TokenUsage.fromChatResponse` builds from `promptTokens` and `completionTokens`.

A live probe through `ChatClientFactory` confirmed what OpenRouter returns:

```
nativeUsage.class = com.openai.models.completions.CompletionUsage
additionalPropertyKeys = [cost, cost_details, is_byok]
cost = 7.6E-5
cost_details = {upstream_inference_cost=7.6E-5, upstream_inference_prompt_cost=2.1E-5,
                upstream_inference_completions_cost=5.5E-5}
is_byok = false
```

`cost` is what OpenRouter charges and is the correct figure for our accounting; `upstream_inference_cost` is what the underlying provider charged OpenRouter. The same extraction already runs in the `sourcelabs-administration` project via `nativeUsage._additionalProperties()["cost"]`, so the approach is proven against this exact Spring AI and OpenRouter combination.

Two constraints shape the design. First, `LlmPipeline` gates generation on the estimate at two points, so any change in how cost is derived changes when episodes get produced. Second, `CachingChatModel.reconstructResponse` already replays stored token counts into a `DefaultUsage`, which means a cache hit is currently costed as though the call ran.

## Goals / Non-Goals

**Goals:**
- Use OpenRouter's authoritative cost where it is available, instead of a hand-maintained estimate.
- Keep every cost path working for providers that report nothing.
- Make an actual charge distinguishable from an estimate wherever cost is persisted or displayed.
- Leave the budget gate's observable behaviour unchanged.

**Non-Goals:**
- Removing the configured per-model rates. They remain the fallback and still drive pre-flight estimates (`estimatePipelineCostCents`, `estimateScoringCostCents`) which run *before* any call and therefore have no reported cost to use.
- Changing TTS cost, which stays character-based.
- Recording `cost_details` or `is_byok`. Captured here as available, but nothing consumes them yet.
- Backfilling historical episodes. There is no source for a reported cost that was never captured.
- Per-stage cost sources. A single aggregate source per episode is enough to answer "is this number real?"; per-stage granularity can follow if it proves necessary.

## Decisions

**Replay the stored cost on a cache hit rather than recording zero.**
A cache hit spends nothing, so recording 0 would be defensible, but it would change today's behaviour: the cache already replays token counts, so cached stages currently carry their full estimated cost. Recording zero would mean a regenerated episode reports near-nothing and could pass a budget gate the original run failed. Replaying keeps the episode cost answering "what does this episode cost to produce", which is the question the gate is actually protecting. The source is recorded as `API_CACHED` so the replay is never mistaken for a fresh charge.

**Carry the replayed cost in `ChatResponseMetadata`, not in a synthetic native usage object.**
`reconstructResponse` builds a `DefaultUsage`, which has no native usage to hang extra properties from. Faking a `CompletionUsage` to smuggle a `cost` key would couple our cache to the OpenAI client's internal shape for no benefit. Instead the cache puts the cost into the response metadata under a known key, and `TokenUsage.fromChatResponse` resolves in order: metadata key first (a cache replay), then `nativeUsage` additional properties (a live call), then nothing. Both paths converge on the same `TokenUsage` field.

**Resolve cost as reported → table → unknown, in `CostEstimator`.**
This mirrors the resolution already proven in `sourcelabs-administration` and keeps one decision point. The alternative — resolving at each call site — would scatter the precedence rule across `ArticleScoreSummarizer`, `LlmPipeline` and `EpisodeRecapGenerator`.

**Record one aggregate source per episode, with a `MIXED` value.**
Stages can legitimately differ: compose may route through OpenRouter and report a real cost while a direct-`openai` filter stage falls back to the table. A single column takes `API` when every contributing stage reported, `TABLE` when none did, `MIXED` when some did, and `UNKNOWN` when no cost could be determined at all. Four per-stage columns would be more precise but quadruple the schema change to answer a question the aggregate already answers.

**Keep persisted costs in integer cents; use the reported value at full precision.**
The existing split stands: `*_cost_cents` columns and the budget gate use rounded integer cents, while the API breakdown exposes fractional cents as `Double`. A reported cost of `7.6E-5` USD is 0.0076 cents, far below one cent, so it must flow through the exact path (`estimateLlmCostCentsExact`) to stay visible, exactly as sub-cent token-derived costs already do.

**Prefer the persisted reported cost over recomputation in the API breakdown.**
The current spec has each stage row recompute `costCents` from persisted tokens times the configured rate, specifically so sub-cent costs survive rounding. That recomputation must not override a real charge. The row now prefers a persisted reported cost, and only recomputes from tokens when there is none.

## Risks / Trade-offs

- **The reported cost is only present for OpenRouter** → The table fallback stays, and the recorded source makes the difference visible rather than silent. Nothing regresses for the direct `openai` provider.
- **`_additionalProperties()` is not a stable Spring AI API surface** → It is the same access path already in production in `sourcelabs-administration`. The extraction is confined to one function in `TokenUsage` and wrapped so a shape change degrades to the table fallback rather than throwing into the pipeline.
- **The budget gate starts seeing real numbers instead of estimates** → Where the configured rate was wrong, the gate's effective threshold shifts. This is the point of the change, but it means an episode that previously squeaked under the limit could now exceed it, or vice versa. Worth watching after rollout, and the reason the cache replay decision was made conservatively.
- **Pre-flight estimates and actuals can now disagree** → `estimatePipelineCostCents` runs before any call and must use the table, so a run can be admitted on an estimate and then record a materially different actual. Acceptable: the gate has always been an estimate at admission time.
- **Cached rows written before this change have no reported cost** → They replay as before via the token path and are recorded as `TABLE`. The cache is a performance artifact and expires; no backfill is warranted.
- **A negative or malformed `cost` value** → Treated as absent and falls back to the table, matching the `signum() >= 0` guard already used in `sourcelabs-administration`.
