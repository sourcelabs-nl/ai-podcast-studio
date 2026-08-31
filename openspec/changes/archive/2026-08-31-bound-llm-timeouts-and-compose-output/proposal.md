## Why

Two ceilings on the LLM path are set wrong in opposite directions, and both cost an episode on 31 August 2026.

**One blanket request timeout, sized for the slowest stage.** `ChatClientFactory` builds every chat model with `Duration.ofMinutes(20)`, so a scoring call inherits the allowance composition needs. During the retry of episode 191 a single article-scoring call hung from 22:28 to 22:41 and stalled the whole run for 13 minutes on the 178th of 178 articles, while the other 177 had each returned in seconds.

The allowance is genuinely needed by compose and only by compose. Measured wall-clock across runs:

| stage | observed |
|---|---|
| scoring (per article) | seconds |
| dedup | 1m 44s, historic max 3m 06s |
| compose | 1m 03s to **18m 11s** |

So the timeout cannot simply be lowered: an 18-minute compose run is real, and a 15-minute ceiling would have failed that episode. It has to be set per stage.

**No output ceiling on compose at all.** `BriefingComposer`, `DialogueComposer` and `InterviewComposer` pass only `model` and `temperature`, so nothing bounds the response. OpenRouter therefore reserves the model's entire output window when it checks affordability, and episode 191 failed at compose with:

```
402: This request requires more credits, or fewer max_tokens.
You requested up to 131072 tokens, but can only afford 91045.
```

The request was blocked over a 131,072-token reservation for a script that used 1,876 words. Actual compose output across 59 episodes is min 2,540, average 12,032, max 57,546 tokens (the compose model is reasoning-heavy, so output far exceeds the script itself). A cap with real headroom would have let that request through on the same balance, and bounds a runaway response the way the dedup stage is already bounded.

## What Changes

- `ResolvedModel` carries the `PipelineStage` it was resolved for. `ModelResolver` already knows the stage, so the stage travels with the model and no call site has to pass it separately.
- Request timeouts become per-stage, configured under `app.llm.timeouts`: filter 3m, dedup 5m, compose 20m. `ChatClientFactory` selects by the resolved model's stage. Compose keeps the ceiling it demonstrably needs; the stages that never approach it stop inheriting it. Recap resolves the filter model and so takes the filter timeout.
- Compose gains an output ceiling, `app.compose.max-output-tokens` (default 96000), applied by all three composers. It sits well above the 57,546-token maximum observed and well below the model's 131,072 window, so it bounds a runaway response and shrinks the credit reservation without risking a truncated script.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `cost-tracking`: records the per-stage request timeouts and the compose output ceiling as bounds on every LLM call.

## Impact

- Backend: `ResolvedModel`/`ModelResolver` (stage field); `ChatClientFactory` (per-stage timeout, `AppProperties` injected); `LlmProperties` (new `timeouts`); `ComposeProperties` (new `maxOutputTokens`); `BriefingComposer`, `DialogueComposer`, `InterviewComposer` (set `maxTokens`).
- Tests: timeout-per-stage selection, the compose cap, and updated `ResolvedModel` constructions.
- Config: `app.llm.timeouts.*` and `app.compose.max-output-tokens` added to `application.yaml`.
- No schema, API or frontend change.
