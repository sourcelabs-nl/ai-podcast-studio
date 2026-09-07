## Why

`OpenRouterRouting` treated a reasoning effort of `none` as "send no `reasoning` block at all". That is not what OpenRouter does with an absent parameter: it infers the model's own default. The dedup and filter model `~deepseek/deepseek-v4-flash-latest` reports `default_enabled: true` at `default_effort: "high"`, so sending nothing bought high-effort reasoning. Measured against the live API on that model with a one-line task:

| request | reasoning tokens |
| --- | --- |
| no `reasoning` block | 47 |
| `reasoning: { effort: "low", exclude: true }` | 22 |
| `reasoning: { effort: "none", exclude: true }` | 0 |
| `reasoning: { enabled: false }` | 0 |

Reasoning tokens are billed as output and drawn from the same `max_tokens` a stage sets for its own output, so on a real dedup prompt they consumed the entire budget and the call returned empty content. Episode 200 failed twice this way, five attempts across 28 minutes: three cut off blind at the request timeout, one returning `Empty response from LLM for topic dedup filter` after 3m54s. Healthy dedup runs return in 21s to 1m44s.

The stage did previously suppress reasoning, via `reasoningEffort("none")` on the flat `reasoning_effort` field, and ran healthily for two months. `fb4ed53` moved reasoning into the extra body — correctly, since OpenRouter does not read the flat field — but defined `none` as "send nothing", on the premise that the model was non-reasoning. Episode 200 was the first generation run after that commit landed.

## What Changes

- `OpenRouterRouting.extraBodyFor` sends a `reasoning` block for every stated effort, `none` included, as `{ "effort": "none", "exclude": true }`. Omitting the block is no longer treated as a way to ask for no reasoning.
- This fixes all three stages that ask for no reasoning: topic dedup, article scoring and recap generation. Each was paying for the routed model's default reasoning.
- `TopicDedupFilter` logs the response's finish reason when it is not a normal stop, together with the output-token budget, so budget exhaustion is diagnosable rather than surfacing as `Error reading response`.
- Corrects the standing claims, in the specs and in the code comments, that these stages take their non-reasoning behaviour from the model they run on.

Not in scope, having been considered and rejected on evidence:

- **A per-stage effort override for dedup.** `effort: "low"` measured 22 reasoning tokens on a one-line task but consumed all 23,940 tokens of a real dedup budget, so a bounded effort does not solve this; only `none` does.
- **Raising the dedup output-token budget, timeout, or retry count.** All three were justified only by reasoning being unavoidable. With reasoning genuinely off, dedup returns in about 1m20s against a 5-minute ceiling, so the existing values stand unchanged.
- **`reasoning: { enabled: false }`.** Measures 0 reasoning tokens but appears nowhere in OpenRouter's documentation, which shows only `enabled: true`. `effort: "none"` is documented as "Disables reasoning entirely" and measures the same, so it is preferred.
- **Streaming the dedup response.** Verified against the pinned `openai-java 4.39.1` sources: `OpenAIOkHttpClient.builder().timeout(d)` maps `d` onto okhttp's `callTimeout`, `createStreaming` shares the non-streaming code path with no special-casing, and `callTimeout` is a hard wall-clock deadline that arriving bytes do not reset. Streaming would add progress visibility but would not let a long call survive, and `spring-ai-openai 2.0.0` exposes no per-call timeout override. It also conflicts with the `spring-ai` skill's Rule SA1 and would silently drop dedup out of the LLM cache, since `CachingChatModel.stream` is a bare delegate.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `cost-tracking`: the requirement that an effort of `none` sends no `reasoning` block is replaced by one requiring every effort to be stated explicitly, including `none`, with the per-model caveat that a `mandatory: true` endpoint rejects it.
- `article-dedup-filter`: the requirement asserting the stage takes its non-reasoning behaviour from its model now requires an explicit `effort: "none"`; a new requirement covers finish-reason logging.

## Impact

- `src/main/kotlin/com/aisummarypodcast/llm/OpenRouterRouting.kt`: a `reasoning` block is now built for any non-null effort.
- `src/main/kotlin/com/aisummarypodcast/llm/TopicDedupFilter.kt`: finish-reason logging, and the stale comment claiming the model supplies the non-reasoning behaviour.
- `src/main/resources/application.yaml`: corrected comments on the dedup model default and on `app.compose.reasoning-effort`, which previously suggested `none` as a cost lever without noting that some endpoints reject it.
- `src/test/kotlin/com/aisummarypodcast/llm/OpenRouterRoutingTest.kt`, `TopicDedupFilterTest.kt`: the `none` behaviour inverts, and the finish-reason predicate is new.
- No database, API or frontend impact. Cost effect is a small reduction: scoring alone made 40 calls for episode 200, each previously carrying unwanted reasoning.
- **`app.compose.reasoning-effort: none` now fails loudly.** `z-ai/glm-5.3` rejects `effort: "none"` with HTTP 400, *"Reasoning is mandatory for this endpoint and cannot be disabled."* No live configuration sets it (the default is `medium`, and this podcast overrides only `temperature`), so the risk is latent. A 400 naming the cause is preferable to silently getting the provider's default, which is what the old behaviour did.
