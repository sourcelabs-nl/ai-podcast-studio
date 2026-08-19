## Why

Z.ai released GLM-5.3 and OpenRouter now serves it. The registry only knows GLM-5.2, so GLM-5.3 cannot be selected as a per-podcast stage override: `GET /config/defaults` builds `availableModels` from `app.models`, and the settings UI offers exactly what that endpoint returns.

## What Changes

- Add `z-ai/glm-5.3` to the `app.models.openrouter` registry as an LLM entry priced at 1.40 input and 4.40 output USD per million tokens, verified against the OpenRouter model and pricing pages rather than estimated.
- `z-ai/glm-5.2` stays registered, and the configured `compose` default stays on GLM-5.2. This change makes GLM-5.3 selectable; it does not switch any podcast onto it.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `model-registry`: the OpenRouter registry gains the `z-ai/glm-5.3` entry.

## Impact

- Configuration: one entry in `src/main/resources/application.yaml` under `app.models.openrouter`.
- No code change. `AppProperties` loads the registry generically, and `/config/defaults` derives `availableModels` from it, so the model appears in the settings dropdown with no frontend work.
- Cost tracking: OpenRouter reports its own per-call cost and the pipeline prefers that reported value, so these rates act as a fallback for calls that report nothing and for the pre-flight estimates that run before any call.
- Unlike GLM-5.2, whose configured price reflects the cheapest of several routed providers, Z.ai is currently the only provider serving GLM-5.3, so its list price is the routed price.
