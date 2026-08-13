## Why

The OpenRouter model registry lags the provider catalog: Anthropic's Claude 5 generation (Sonnet 5, Opus 5) and DeepSeek's "latest V4 Flash" routing alias are all available on OpenRouter but absent from `app.models.openrouter`, which only lists the Claude 4.x generation and pinned DeepSeek releases. A model that is not in the registry cannot be selected as a per-podcast stage override from the dashboard, and any stage that did use it would run without cost tracking (`ResolvedModel.cost = null`).

## What Changes

- Add three LLM entries to the `app.models.openrouter` registry in `application.yaml`:
  - `anthropic/claude-sonnet-5`: input $2.00 / output $10.00 per Mtok
  - `anthropic/claude-opus-5`: input $5.00 / output $25.00 per Mtok
  - `~deepseek/deepseek-v4-flash-latest`: input $0.08 / output $0.252 per Mtok
- The DeepSeek entry is an OpenRouter alias (the leading `~` is part of the slug) that always routes to the newest V4 Flash release, currently `deepseek-v4-flash-0731`. It is added alongside the existing pinned `deepseek/deepseek-v4-flash` entry rather than replacing it.
- All three become selectable as per-podcast stage overrides (filter / dedup / compose) because `GET /config/defaults` derives `availableModels` directly from the registry.
- Pricing was read from the OpenRouter models API (`https://openrouter.ai/api/v1/models`), not from training data, per the `application.yaml` pricing rule.
- No change to the global stage defaults (`app.llm.defaults` still points at `openai/gpt-5.4-nano`, `deepseek/deepseek-v4-flash`, `z-ai/glm-5.2`), so existing podcasts are unaffected.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `model-registry`: the requirement covering registry contents is extended to name the new OpenRouter entries with their per-Mtok pricing, and to state that alias slugs are permitted registry keys whose cost tracking is approximate.

## Impact

- `src/main/resources/application.yaml` (three registry entries)
- No Kotlin, database, or frontend changes: `AppProperties.models` is a plain map keyed by model slug, and the frontend model picker is driven by `GET /config/defaults`.
- Already implemented and verified live; this change documents it retroactively per the project's OpenSpec workflow rule.
