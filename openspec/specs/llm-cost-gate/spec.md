# Capability: LLM Cost Gate

## Purpose

Pre-flight LLM cost estimation and pipeline gating based on configurable per-podcast cost thresholds.
## Requirements
### Requirement: Pre-flight LLM cost estimation
The system SHALL estimate the total LLM cost before making any API calls in `LlmPipeline.run()`. The estimation SHALL run after post aggregation (so article count and content are known) but before scoring/summarization. The estimate SHALL cover both pipeline stages: scoring/summarization (filter model) and composition (compose model).

Input token estimation SHALL use `content.length / 4` (character count divided by 4) for each article's body text. Scoring output tokens SHALL be estimated as a fixed 200 tokens per article. Composition input tokens SHALL be estimated as `N_articles × (200 × 4)` characters (assuming ~200-token summaries per article, converted back to characters for the chars/4 formula). Composition output tokens SHALL be estimated as `targetWords × 1.3` (words to tokens conversion), using the podcast's `targetWords` or the global default.

The estimation SHALL assume all articles pass relevance filtering (pessimistic estimate — every article is included in composition).

The estimated cost SHALL be calculated using the existing `CostEstimator` logic with the configured per-model pricing (`inputCostPerMtok`, `outputCostPerMtok`).

#### Scenario: Cost estimated for 10 articles with default models
- **WHEN** 10 articles with an average of 2000 characters each are pending, using the `cheap` model (input: $0.15/Mtok, output: $0.60/Mtok) for scoring and `capable` model (input: $3.00/Mtok, output: $15.00/Mtok) for composition, with `targetWords` of 1500
- **THEN** the system estimates scoring input as `10 × (2000 / 4) = 5000` tokens, scoring output as `10 × 200 = 2000` tokens, composition input as `10 × 200 = 2000` tokens, composition output as `1500 × 1.3 = 1950` tokens, and calculates the total cost using both models' pricing

#### Scenario: Cost estimated with varying article lengths
- **WHEN** 3 articles with body lengths of 500, 3000, and 8000 characters are pending
- **THEN** the system estimates scoring input tokens as `(500/4) + (3000/4) + (8000/4) = 2875` tokens and uses the sum for cost calculation

### Requirement: Pipeline gating based on cost threshold
The system SHALL compare the estimated LLM cost against the podcast's effective cost threshold. If the estimated cost exceeds the threshold, the pipeline SHALL skip the entire run (no scoring, no composition) and return null.

The effective threshold SHALL be the podcast's `maxLlmCostCents` if set, otherwise the global `app.llm.max-cost-cents` value (default: 200 cents).

When the gate triggers, the system SHALL log a warning message that includes: the podcast ID, the estimated cost in cents, and the configured threshold in cents. The log message SHALL be at WARN level.

When the gate does not trigger, the system SHALL log the estimated cost at INFO level and proceed with the pipeline.

If cost pricing is not configured for either model (i.e., `inputCostPerMtok` or `outputCostPerMtok` is null), the gate SHALL NOT block the pipeline — it SHALL log a warning that cost estimation is unavailable and proceed.

#### Scenario: Estimated cost below threshold — pipeline proceeds
- **WHEN** the estimated cost is 150 cents and the effective threshold is 200 cents
- **THEN** the pipeline proceeds with scoring and composition

#### Scenario: Estimated cost exceeds threshold — pipeline skipped
- **WHEN** the estimated cost is 350 cents and the effective threshold is 200 cents
- **THEN** the pipeline returns null, no LLM calls are made, and a WARN log is emitted: `"[LLM] Cost gate triggered for podcast {}: estimated {}¢ exceeds threshold {}¢ — skipping pipeline"`

#### Scenario: Estimated cost exactly equals threshold — pipeline proceeds
- **WHEN** the estimated cost is 200 cents and the effective threshold is 200 cents
- **THEN** the pipeline proceeds (threshold is exclusive — only exceeded values are blocked)

#### Scenario: No articles to process — gate not evaluated
- **WHEN** there are no unscored articles and no relevant unprocessed articles
- **THEN** the pipeline returns null via existing logic without evaluating the cost gate

#### Scenario: Model pricing not configured — gate bypassed
- **WHEN** the filter or compose model does not have `inputCostPerMtok` or `outputCostPerMtok` configured
- **THEN** the pipeline proceeds with a WARN log: `"[LLM] Cost estimation unavailable for podcast {} — pricing not configured for model(s), skipping cost gate"`

#### Scenario: Per-podcast threshold overrides global default
- **WHEN** a podcast has `maxLlmCostCents` set to 500 and the global default is 200, and the estimated cost is 350 cents
- **THEN** the pipeline proceeds (350 < 500)

#### Scenario: Per-podcast threshold null — falls back to global
- **WHEN** a podcast has `maxLlmCostCents` set to null and the global default is 200, and the estimated cost is 250 cents
- **THEN** the pipeline is skipped (250 > 200)

### Requirement: Global default cost threshold configuration
The system SHALL support a global default cost threshold via the `app.llm.max-cost-cents` configuration property. The default value SHALL be 200 (cents). This property SHALL be exposed via `AppProperties` as `LlmProperties.maxCostCents`.

#### Scenario: Default threshold applied
- **WHEN** `app.llm.max-cost-cents` is not set in configuration
- **THEN** the global default threshold is 200 cents

#### Scenario: Custom global threshold
- **WHEN** `app.llm.max-cost-cents` is set to 500 in `application.yaml`
- **THEN** podcasts without a per-podcast override use 500 cents as the threshold

### Requirement: Cost gate adds a research buffer when deep-dive is enabled

When a podcast has `deepDiveEnabled=true`, the cost gate estimator SHALL add a configurable fixed buffer (default `app.research.cost-buffer-cents=5`) to the estimated total before comparing against `maxLlmCostCents`. When `deepDiveEnabled=false`, the buffer MUST NOT be applied.

#### Scenario: Buffer applied for deep-dive podcasts

- **WHEN** the cost gate estimator runs for a podcast with `deepDiveEnabled=true` and base estimate 50¢, with buffer 5¢
- **THEN** the comparison uses 55¢ against `maxLlmCostCents`

#### Scenario: Buffer omitted for standard podcasts

- **WHEN** the cost gate estimator runs for a podcast with `deepDiveEnabled=false` and base estimate 50¢
- **THEN** the comparison uses 50¢ against `maxLlmCostCents`

