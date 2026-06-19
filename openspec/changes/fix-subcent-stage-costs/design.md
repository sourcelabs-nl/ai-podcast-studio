## Context

This is a retrofit for an already-implemented, localized bug fix. The episode cost breakdown discarded sub-cent stage costs because the entire reporting path used integer cents, while cheap models (e.g. `deepseek-v4-flash`) produce per-stage costs well under a cent.

## Goals

- Make sub-cent stage costs visible in the cost breakdown without a schema migration.
- Leave persistence, aggregate totals, and the LLM cost gate on integer cents (whole-cent granularity is fine for a ~$2.00 budget cap).

## Decisions

### Scoped `Double` in the API response (chosen)
Add `CostEstimator.estimateLlmCostCentsExact(input, output, cost): Double?` that returns fractional cents without rounding. Keep the existing `estimateLlmCostCents(...): Int` (now delegating to the exact variant and rounding) for persistence and the cost gate. Thread `Double` through the cost breakdown DTOs (`LlmStageCostResponse`, `TtsCostResponse`, `ResearchCostResponse`, `EpisodeCostsResponse`) and the `StageCostFn` typealias.

In `buildCosts`, recompute each LLM stage cost from persisted tokens at full precision via the cost function, falling back to the persisted integer-cent value only when the model rate is unknown or there are no tokens. The single-episode GET endpoint already supplies a real `stageCostFnFromModels(appProperties.models)`, so the detail page recomputes accurately; list endpoints (no stage breakdown shown) keep the no-op fallback.

### Alternative rejected: migrate the storage unit to micro-USD
Most correct end-to-end, but a wide refactor across schema, services, budget logic, and frontend for a display-only defect. Out of scope per the project's "no large refactors unless instructed" guideline.

## Risks / Trade-offs

- The `costs.*.costCents` JSON values are now decimals (e.g. `0.084`) rather than integers. The frontend already types these as `number` and formats to four decimals, so no consumer breaks.
- Stage rows recompute from current `app.models` pricing rather than the historical persisted cents. Acceptable: the displayed breakdown reflects configured rates, and the persisted aggregate (used for the gate) is unchanged. When a model rate is missing, the row falls back to the persisted value.
