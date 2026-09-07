## Context

See `proposal.md` for motivation and the measured reasoning-token table. The constraints that shaped the approach:

- `OpenRouterRouting.extraBodyFor` is shared by every OpenRouter stage. Compose passes a configured effort; dedup, scoring and recap pass `NO_REASONING`. Only the handling of that last value was wrong.
- `extraBody` genuinely reaches the provider. Verified through the pinned sources: `OpenAiChatModel.createRequest` reads `getExtraBody()` and applies `builder.additionalBodyProperties(...)`, `JsonValue.from` preserves nested maps, `Body.additionalProperties` is serialized via `@JsonAnyGetter` as top-level siblings, and both `copy()` and `combineWith(...)` carry `extraBody` through ChatClient's option merge. So the request shape is worth fixing; nothing downstream discards it.
- `provider.require_parameters` is `true`. OpenRouter's docs do not describe how `reasoning` interacts with it, so this was settled empirically: `effort: "none"` routes successfully on the dedup and filter model.
- `effort: "none"` is not universally available. An endpoint reporting `mandatory: true` rejects it with HTTP 400.
- The dedup model in production is the podcast's override `~deepseek/deepseek-v4-flash-latest`, not the `application.yaml` default. The podcast overrides all three stages.

## Goals / Non-Goals

**Goals:**

- The three structured stages actually get no reasoning, rather than the routed model's default.
- `NO_REASONING` means what its name says.
- Budget exhaustion is distinguishable from a transport fault in the logs.

**Non-Goals:**

- Changing any model, timeout, retry count or token budget (see `proposal.md` - What Changes for why each was rejected on measurement).
- Streaming the dedup response.
- Per-model capability detection. The code sends the configured effort and lets an incompatible endpoint reject it.

## Decisions

**Send `effort: "none"` rather than `enabled: false`.** Both measured 0 reasoning tokens on the production model. `effort: "none"` is documented ("Disables reasoning entirely"); `enabled: false` appears nowhere in OpenRouter's documentation, which shows only `enabled: true` described as "inferred from `effort` or `max_tokens`". Between two options with identical measured behaviour, the documented one wins.

**Send `none` despite it being absent from the model's `supported_efforts`.** OpenRouter reports `supported_efforts: ["max", "high", "low"]` for this model, which suggested `none` would be refused or ignored. Direct probing showed it is honoured and routes fine, so `supported_efforts` is not a reliable guide to whether `none` is accepted; `mandatory` is. This is why the decision rests on measurement rather than on the metadata.

**Fix the shared helper rather than adding a dedup-specific override.** The bug is in the meaning of `none`, not in dedup's use of it. Scoring and recap pass the same value and were paying for the same unwanted reasoning — scoring across 40 calls for a single episode. A dedup-only override would have left them broken and left the misleading constant in place. The cost is that a `mandatory: true` model now rejects a configured `none`, which is accepted deliberately (see below).

**Let an incompatible effort fail loudly.** The alternative is to keep omitting the block for models that reject `none`, which is precisely the silent fallback that caused this incident: the request appeared to ask for no reasoning and received high effort. A 400 saying "Reasoning is mandatory for this endpoint" is actionable; a silent default is not. No live configuration is affected, since `app.compose.reasoning-effort` defaults to `medium` and no podcast overrides it.

**Log the finish reason only when abnormal.** A WARN on every call would be noise, and the diagnostic value is entirely in the `LENGTH` case. A missing finish reason is also not warned about: a provider that reports nothing says nothing about the budget, and warning there would bury the case this exists to surface. The budget is logged alongside the reason, because the reason alone does not say what the cap was.

## Risks / Trade-offs

- **A stage's model rejects `effort: "none"`** → Fails fast with a 400 naming the cause, on the first call rather than degrading quietly. Documented on `app.compose.reasoning-effort`, whose comment previously recommended `none` as a cost lever without the caveat.
- **`supported_efforts` may become authoritative later, or `none` may stop routing** → The finish-reason WARN and the `[Dedup] Filter complete in ...` timing line make a regression visible on the first run, which is how this incident was diagnosed in one attempt rather than five.
- **Scoring and recap change behaviour without their own verification run** → Both were exercised by the verification episode: scoring made 40 calls and recap ran, and the episode completed. Their reasoning was previously unwanted cost rather than correctness, so a change in reasoning volume cannot break their output shape.
- **The `application.yaml` default filter model (`openai/gpt-5.4-nano`) returns 404 against the quantization floor** — "No endpoints found for the request with quantization: fp8,fp16,bf16,fp32". This predates this change and does not affect production, which overrides the filter model. Flagged, not fixed here.

## Migration Plan

Request-shaping only: no schema change, no data migration, no API change. Deploy is `./stop.sh && ./start.sh`. Rollback is reverting the change; nothing persisted needs undoing.

Verified by retrying episode 200 through the REST API against the live provider. Dedup completed in 1m19.5s (133 candidates → 132 selected across 82 clusters) on a single call using 20,890 input and 4,124 output tokens for 0.20 cents, against a prior baseline of 2,255-3,840 output tokens and 21s-1m44s. The episode reached `GENERATED` and published.
