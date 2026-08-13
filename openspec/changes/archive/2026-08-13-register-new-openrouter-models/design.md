## Context

The model registry is pure configuration: `app.models.<provider>.<model>` is bound to `AppProperties.models` as a `Map<String, Map<String, ModelCost>>`, and `ConfigController.defaults()` derives its `availableModels` response directly from that map. No Kotlin code enumerates model names, so making a new OpenRouter model selectable and cost-tracked is a YAML-only addition.

## Goals / Non-Goals

**Goals:**
- Make `anthropic/claude-sonnet-5`, `anthropic/claude-opus-5`, and `~deepseek/deepseek-v4-flash-latest` selectable as per-podcast stage overrides with cost tracking.
- Keep pricing traceable to the provider's published numbers.

**Non-Goals:**
- Changing the global stage defaults in `app.llm.defaults`. Existing podcasts keep their current models; adopting a new model is a per-podcast choice made in the dashboard.
- Registering the `:batch` or `-fast` OpenRouter variants of the Anthropic models. They are separate catalog entries with different pricing and no current use case in the pipeline.
- Retiring the Claude 4.x or pinned `deepseek/deepseek-v4-flash` entries. Podcasts may still reference them, and removing an entry would silently drop cost tracking for those stages (`ResolvedModel.cost = null`).

## Decisions

**Pricing read from the OpenRouter models API, not from model knowledge.** `https://openrouter.ai/api/v1/models` reports `anthropic/claude-sonnet-5` at $0.000002/$0.00001 per token, `anthropic/claude-opus-5` at $0.000005/$0.000025, and `~deepseek/deepseek-v4-flash-latest` at $0.000000079996/$0.000000252, which convert to 2.00/10.00, 5.00/25.00, and 0.08/0.252 USD per Mtok. The `application.yaml` authoring rule forbids guessed pricing, and provider prices change often enough that training-data values are unreliable.

**The DeepSeek alias keeps its leading `~`.** OpenRouter's catalog reports the id and canonical slug as `~deepseek/deepseek-v4-flash-latest`; the tilde-less form returns 404. The registry key must be the slug that is sent to the API, so the tilde is part of the key. YAML map keys in this file are already bracket-quoted (`"[...]"`) for the slashes, which covers the tilde too.

**Alias pricing is accepted as approximate, and the pinned entry stays.** An alias resolves to whichever release is newest, so its real cost can drift from the alias's listed price without any config change. That is acceptable for a cost estimate but not for a stage that needs a stable model, so the pinned `deepseek/deepseek-v4-flash` entry remains available (and is still the `dedup` default). A YAML comment on the alias entry records that its cost tracking is approximate by design.

**Per-entry price comments only where routing is ambiguous.** The existing DeepSeek Pro and GLM entries carry comments explaining that their price reflects a specific non-ZDR upstream provider chosen by OpenRouter routing. Anthropic models are served first-party at a single price, so those entries need no comment.

## Risks / Trade-offs

- **Pricing drift**: the values are a snapshot. If OpenRouter changes them, cost tracking silently reports stale figures until the registry is updated. This risk is identical for every existing entry, and the mitigation is the existing rule to re-verify pricing whenever these values are touched. It is structurally larger for the alias, whose target changes on DeepSeek's schedule rather than ours.
- **Alias reasoning defaults**: V4 Flash enables reasoning by default on OpenRouter, whose hidden reasoning tokens can exhaust the output cap and return empty content. `TopicDedupFilter` already sets `reasoningEffort("none")` unconditionally, so the dedup stage is protected whichever V4 Flash slug is selected; the filter and compose stages do not disable reasoning, so selecting the alias there carries the same exposure as the existing pinned entry.
- **Not exercised against the live models**: the registry addition itself was verified end-to-end (all three entries appear in `GET /config/defaults` after a restart), but no episode has yet been generated with any of them selected, so their output quality and real token spend are unmeasured.
