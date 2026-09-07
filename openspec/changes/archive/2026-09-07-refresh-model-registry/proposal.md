## Why

The stage defaults in `application.yaml` had drifted out of usability. The filter default `openai/gpt-5.4-nano` was **unroutable**: `OpenRouterRouting` restricts routing to endpoints reporting an fp8-or-better quantization, and a vendor-native endpoint (OpenAI, Anthropic, Google) reports its quantization as `unknown`, which the floor rejects. All 4 of that model's endpoints are vendor-native, so any request returns `404 No endpoints found`. The code-level `StageDefaults.dedup` was `anthropic/claude-sonnet-4.6`, unroutable for the same reason, and disagreed with the yaml besides.

Six registry entries are unroutable on that basis — `openai/gpt-5.4-nano` and all five Anthropic entries — yet all six were offered in the settings model picker, where choosing one produces a generation that cannot succeed.

Five pricing entries had also drifted from the provider's current rates, by as much as 3x on output (`z-ai/glm-5.2` was configured at 4.00 against a real 1.32).

## What Changes

- **Stage defaults move to models that route**, chosen on published benchmarks rather than recency alone:
  - `filter` and `dedup`: `openai/gpt-5.4-nano` and `deepseek/deepseek-v4-flash` → `deepseek/deepseek-v4-flash-0731`. The July release beats the April one (canonical slug `deepseek-v4-flash-20260423`) on every Artificial Analysis index — intelligence 40.8 against none published, coding 69.1 against 56.2, agentic 41.9 against 23.8 — while being *cheaper* at the eligible floor ($0.05 against $0.0886 input) and carrying a larger context (1.31M against 1.05M).
  - `compose`: `z-ai/glm-5.2` → `z-ai/glm-5.3`.
- **`StageDefaults` in `AppProperties` is brought into step with the yaml**, and documents that a default must clear the quantization floor.
- **`ModelCost` gains `selectable`** (default `true`). An unselectable entry keeps its pricing but is withheld from the `/config/defaults` picker.
- **The six unroutable entries are marked `selectable: false`** rather than deleted, because a registry entry is how a past episode's cost is resolved: `ModelResolver` returns `cost = null` on a miss, so deleting an entry would silently zero the cost of any episode generated on it.
- **Five prices refreshed** to the cheapest endpoint that clears the floor, since that is what routing can actually select: `deepseek/deepseek-v4-flash` 0.0983/0.1966 → 0.0886/0.1772, `~deepseek/deepseek-v4-flash-latest` 0.08/0.252 → 0.05/0.16, `deepseek/deepseek-v4-pro` 1.30/2.60 → 0.9553/1.9105, `z-ai/glm-5.2` 1.00/4.00 → 0.42/1.32, `z-ai/glm-5.3` 1.40/4.40 → 1.12/3.52. A new entry is added for `deepseek/deepseek-v4-flash-0731` at 0.05/0.16.

`z-ai/glm-5.3` was kept for compose rather than replaced. It is the strongest open-weight model that clears the floor: of the twelve highest-scoring models in the catalogue only two are open-weight, and the other (`moonshotai/kimi-k3`, intelligence 50.2 against 48.6) prices at $2.85/$14.25 at the floor — roughly 4x the output cost — with only 2 of its 18 endpoints eligible. `deepseek/deepseek-v4-pro-0813` was evaluated and rejected: it loses on all three indices (42.1 / 68.8 / 42.5). Human-preference data agrees: on the creative-writing arena's open-weight ranking GLM-5.3 leads the open field, with `qwen/qwen3.8-max` statistically tied but more expensive and, as it happens, blocked by the floor too.

Out of scope: relaxing the quantization floor. That would open the vendor-native frontier models (`claude-fable-5.1` at intelligence 56.8, `openai/gpt-6-astra` at 54.7) but re-admit the fp4 and unknown endpoints that `e744e91` was written to exclude after they produced a script with no speaker tags and one with the wrong podcast name. Explicitly retained as-is.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `model-registry`: a model entry gains an optional `selectable` flag, and the requirement that pricing come from the provider's published rates is sharpened to the cheapest endpoint that clears the routing floor.
- `config-defaults-api`: `availableModels` returns only selectable models.

## Impact

- `src/main/resources/application.yaml`: `app.llm.defaults` for all three stages, six `selectable: false` flags, five refreshed prices, one new entry.
- `src/main/kotlin/com/aisummarypodcast/config/AppProperties.kt`: `ModelCost.selectable`, `StageDefaults` values and KDoc.
- `src/main/kotlin/com/aisummarypodcast/config/ConfigMappers.kt` (new): `toSelectableModels`, keeping the filtering out of the controller.
- `src/main/kotlin/com/aisummarypodcast/config/ConfigController.kt`: delegates to that mapper.
- `src/test/kotlin/com/aisummarypodcast/config/ConfigMappersTest.kt` (new), and a `ModelResolverTest` case locking in that an unselectable model still resolves a cost.
- No database or frontend change. The frontend already groups `availableModels` by provider and filters by type, so a shorter list needs no client change; provider keys are retained even when emptied so the settings page's active-provider selection does not shift.
- Production behaviour is unchanged for the existing podcast, which overrides all three stages (`~deepseek/deepseek-v4-flash-latest` for filter and dedup, `z-ai/glm-5.3` for compose). The defaults apply to podcasts that set no override.
