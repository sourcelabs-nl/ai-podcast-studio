## ADDED Requirements

### Requirement: Cost gate adds a research buffer when deep-dive is enabled

When a podcast has `deepDiveEnabled=true`, the cost gate estimator SHALL add a configurable fixed buffer (default `app.research.cost-buffer-cents=5`) to the estimated total before comparing against `maxLlmCostCents`. When `deepDiveEnabled=false`, the buffer MUST NOT be applied.

#### Scenario: Buffer applied for deep-dive podcasts

- **WHEN** the cost gate estimator runs for a podcast with `deepDiveEnabled=true` and base estimate 50¢, with buffer 5¢
- **THEN** the comparison uses 55¢ against `maxLlmCostCents`

#### Scenario: Buffer omitted for standard podcasts

- **WHEN** the cost gate estimator runs for a podcast with `deepDiveEnabled=false` and base estimate 50¢
- **THEN** the comparison uses 50¢ against `maxLlmCostCents`
