## Why

The episode cost breakdown stores money as integer cents, so any stage cost under half a cent rounds to `0` and the UI renders it as "—". Cheap models like `deepseek-v4-flash` (~$0.10/Mtok) never reach a whole cent for scoring or recap, so their costs were invisible even though the breakdown already formats values to four decimals ($0.0001). The precision was being discarded before it reached the display.

## What Changes

- The cost breakdown DTOs (`LlmStageCostResponse`, `TtsCostResponse`, `ResearchCostResponse`, `EpisodeCostsResponse`) now expose `costCents` / `totalCostCents` as fractional cents (`Double`) instead of `Int`.
- The breakdown now always recomputes each LLM stage cost from the persisted token totals and the model rate at full precision (via a new `CostEstimator.estimateLlmCostCentsExact`), falling back to the persisted integer-cent value only when the model rate is unknown or there are no tokens.
- Persistence (`*_cost_cents` columns, aggregate `llm_cost_cents`) and the LLM cost gate continue to use the rounded integer-cent value. No schema migration.
- The frontend cost types already use `number` and `formatCents` already renders four decimals — no frontend change required.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `cost-tracking`: the "Episode cost breakdown surfaced in API" requirement changes from integer-cent `costCents` to fractional-cent values recomputed from tokens, so sub-cent stage costs stay visible.

## Impact

- Backend: `CostEstimator`, `PodcastMappers` (`StageCostFn`, `stageCostFnFromModels`, `buildCosts`), `PodcastDtos` (cost breakdown DTOs).
- API: the `costs.*.costCents` and `costs.totalCostCents` fields in `EpisodeResponse` are now decimals (e.g. `0.084`) rather than whole integers. The top-level `llmCostCents` / `ttsCostCents` and the `maxLlmCostCents` budget cap are unchanged (integer cents).
- Tests: `CostEstimatorTest`, `EpisodeCostsMapperTest`.
