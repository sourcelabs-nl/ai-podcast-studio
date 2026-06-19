## 1. Cost estimator

- [x] 1.1 Add `CostEstimator.estimateLlmCostCentsExact(input, output, cost): Double?` returning fractional cents without rounding
- [x] 1.2 Reimplement `estimateLlmCostCents` to delegate to the exact variant and round to `Int`

## 2. Cost breakdown DTOs and mapper

- [x] 2.1 Change `costCents` / `totalCostCents` to `Double` in `LlmStageCostResponse`, `TtsCostResponse`, `ResearchCostResponse`, `EpisodeCostsResponse`
- [x] 2.2 Change `StageCostFn` to return `Double?` and back `stageCostFnFromModels` with `estimateLlmCostCentsExact`
- [x] 2.3 Update `buildCosts` to recompute each LLM stage cost from tokens at full precision, falling back to persisted cents when the rate is unknown or there are no tokens; coerce TTS/research cents to `Double`

## 3. Tests

- [x] 3.1 Add `CostEstimatorTest` cases for `estimateLlmCostCentsExact` (sub-cent precision + null pricing)
- [x] 3.2 Update `EpisodeCostsMapperTest` assertions to `Double` literals
- [x] 3.3 `mvn test` for `CostEstimatorTest` and `EpisodeCostsMapperTest` passes

## 4. Verification

- [x] 4.1 Restart the app and confirm `GET .../episodes/150` returns sub-cent `costs.score.costCents`
- [x] 4.2 Confirm the Costs tab renders the deepseek-v4-flash scoring/recap costs (no longer "—")
