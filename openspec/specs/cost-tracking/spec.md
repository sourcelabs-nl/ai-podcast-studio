# Capability: Cost Tracking

## Purpose

Estimate and report LLM and TTS costs per episode based on per-model pricing configured in `app.models`.
## Requirements
### Requirement: TTS cost estimation
The system SHALL estimate TTS costs by looking up the model's `cost-per-million-chars` from the unified `app.models` configuration. The lookup SHALL use the TTS provider name and model name to find the entry at `app.models.<provider>.<model>`. For Inworld, the model name SHALL come from `ttsSettings["model"]` (defaulting to `inworld-tts-1.5-max`). For OpenAI TTS, the model name SHALL default to `tts-1-hd`. The estimated TTS cost in cents SHALL be calculated as: `(characters * costPerMillionChars / 1_000_000 * 100).roundToInt()`. If TTS pricing is not configured for the provider/model, estimated cost SHALL be null.

#### Scenario: OpenAI TTS cost estimated
- **WHEN** `app.models.openai.tts-1-hd` has `cost-per-million-chars: 15.00` and an episode uses 8000 characters with `ttsProvider: "openai"`
- **THEN** TTS cost is `(8000 * 15.00 / 1_000_000 * 100).roundToInt()` = 12 cents

#### Scenario: Inworld TTS Max cost estimated
- **WHEN** `app.models.inworld.inworld-tts-1.5-max` has `cost-per-million-chars: 10.00` and an episode uses 8000 characters with `ttsProvider: "inworld"` and default model
- **THEN** TTS cost is `(8000 * 10.00 / 1_000_000 * 100).roundToInt()` = 8 cents

#### Scenario: Inworld TTS Mini cost estimated
- **WHEN** `app.models.inworld.inworld-tts-1.5-mini` has `cost-per-million-chars: 5.00` and an episode uses 8000 characters with `ttsProvider: "inworld"` and `ttsSettings: {"model": "inworld-tts-1.5-mini"}`
- **THEN** TTS cost is `(8000 * 5.00 / 1_000_000 * 100).roundToInt()` = 4 cents

#### Scenario: TTS pricing not configured for provider/model
- **WHEN** `app.models` has no entry matching the TTS provider and model
- **THEN** TTS estimated cost SHALL be null (character count is still tracked)

### Requirement: Model pricing configuration
Model pricing SHALL be configured in `application.yaml` under `app.models.<provider>.<model>`. LLM models SHALL use `input-cost-per-mtok` and `output-cost-per-mtok` (USD per million tokens). TTS models SHALL use `cost-per-million-chars` (USD per million characters). All cost fields are optional.

#### Scenario: LLM model with pricing configured
- **WHEN** `app.models.openrouter.anthropic/claude-sonnet-4.6` includes `input-cost-per-mtok: 3.00` and `output-cost-per-mtok: 15.00`
- **THEN** the cost estimator uses these values for that model's cost calculations

#### Scenario: TTS model with pricing configured
- **WHEN** `app.models.inworld.inworld-tts-1.5-max` includes `cost-per-million-chars: 10.00`
- **THEN** the cost estimator uses this value for TTS cost calculations

#### Scenario: Model without pricing configured
- **WHEN** a model entry omits cost fields
- **THEN** the cost estimator returns null for estimated cost but usage counts are still tracked

### Requirement: Research cost fields on the episode response

Each episode SHALL track and expose research-related cost fields: `researchCalls` (integer count of `webSearch` invocations) and `researchCostCents` (USD cents, nullable when no research occurred or no pricing is configured).

#### Scenario: Episode without research has zero/null fields

- **WHEN** an episode is generated with `deepDiveEnabled=false`
- **THEN** the episode response has `researchCalls=0` and `researchCostCents` is `null` or `0`

#### Scenario: Episode with research reports counts and cost

- **WHEN** an episode is generated with 2 successful `webSearch` calls and a configured per-call price
- **THEN** the episode response has `researchCalls=2` and `researchCostCents` equals 2 × configured per-call cost in cents

### Requirement: Per-stage episode LLM cost breakdown
The system SHALL persist per-stage LLM token and cost totals on each episode, split across four stages: scoring (Stage 1), dedup (Stage 3), compose (Stage 4), and recap. The columns SHALL be `score_input_tokens`, `score_output_tokens`, `score_cost_cents`, `dedup_input_tokens`, `dedup_output_tokens`, `dedup_cost_cents`, `compose_input_tokens`, `compose_output_tokens`, `compose_cost_cents`, `recap_input_tokens`, `recap_output_tokens`, `recap_cost_cents` (all INTEGER NOT NULL DEFAULT 0). The pre-existing aggregate columns `llm_input_tokens`, `llm_output_tokens`, `llm_cost_cents` SHALL continue to exist but SHALL be maintained as the sum of the four per-stage triples and written through a single code path in `EpisodeService` — no other code path may update them independently.

#### Scenario: Per-stage triples written when each stage runs
- **WHEN** an episode is generated end-to-end with all four LLM stages
- **THEN** `score_*`, `dedup_*`, `compose_*`, `recap_*` triples are each populated with the tokens and cost cents of their respective stage

#### Scenario: Aggregate equals sum of stages
- **WHEN** any LLM stage persists its triple
- **THEN** `llm_input_tokens`, `llm_output_tokens`, `llm_cost_cents` are recomputed as the sum of the four corresponding stage columns, in the same transaction

#### Scenario: Stages that did not run carry zero
- **WHEN** a pipeline run skips a stage (e.g. recap fails and is swallowed)
- **THEN** the corresponding stage triple stays at 0 and the aggregate sums the stages that did run

### Requirement: Score stage cost computed from token totals
The system SHALL compute `score_cost_cents` from the SUM of scoring tokens across the articles linked to the episode, using `CostEstimator.estimateLlmCostCents(totalScoreInput, totalScoreOutput, filterModel.cost)`. The system SHALL NOT compute `score_cost_cents` by summing `articles.llm_cost_cents`, because per-article integer cents round sub-cent calls to 0 and lose precision.

#### Scenario: Score cost preserved despite per-article zero costs
- **WHEN** an episode is generated with 40 articles each costing $0.000123 to score (per-article `llm_cost_cents` rounds to 0)
- **THEN** `score_cost_cents` reflects the total cost ($0.0049 → 0¢ or 1¢ depending on rounding of the SUM), not 0 from summing rounded zeros

#### Scenario: Score cost null-coerced when pricing missing
- **WHEN** the filter model has no pricing configured in `app.models`
- **THEN** `score_cost_cents` is 0 (CostEstimator returns null, coerced at the persistence boundary)

### Requirement: Recap stage cost computed and persisted
The recap stage SHALL compute its cost via `CostEstimator.estimateLlmCostCents(recapUsage.inputTokens, recapUsage.outputTokens, filterModel.cost)` and return it as `RecapResult.costCents`. `EpisodeService.generateAndStoreRecap` SHALL persist this value into `recap_cost_cents` on the episode. Previously (before this change) recap counted tokens but never computed cost, leaking spend out of the aggregate.

#### Scenario: Recap cost populated for new episodes
- **WHEN** an episode is generated with a recap that uses 800 input + 60 output tokens against a model priced $1/$5 per Mtok
- **THEN** `recap_cost_cents` is the rounded cents value for that token spend (8¢ via `(800/1M * 1 + 60/1M * 5) * 100` ≈ 0¢ after rounding; for larger usage the cents become non-zero)

#### Scenario: Recap cost null-coerced when pricing missing
- **WHEN** the filter model has no pricing configured
- **THEN** `recap_cost_cents` is 0 and the recap still completes normally

### Requirement: Backfill of score tokens for pre-V57 episodes
The V57 migration SHALL backfill `score_input_tokens` and `score_output_tokens` for pre-existing episodes from the SUM of `articles.llm_input_tokens` and `articles.llm_output_tokens` joined via `episode_articles`. The migration SHALL NOT backfill `score_cost_cents` because the historical per-article `articles.llm_cost_cents` column rounds sub-cent calls to 0 and is unreliable for aggregation. Dedup, compose, and recap stage columns SHALL stay at 0 for pre-V57 episodes — there is no source data to reconstruct them.

#### Scenario: Score tokens backfilled
- **WHEN** the V57 migration runs against a database containing pre-existing episodes with linked articles
- **THEN** each episode's `score_input_tokens` and `score_output_tokens` equal the sum of its linked articles' `llm_input_tokens` / `llm_output_tokens`

#### Scenario: Stage costs zero for legacy episodes
- **WHEN** a pre-V57 episode is read after the migration
- **THEN** `score_cost_cents`, `dedup_cost_cents`, `compose_cost_cents`, `recap_cost_cents` are all 0 even when the aggregate `llm_cost_cents` is non-zero

### Requirement: Episode cost breakdown surfaced in API
The `EpisodeResponse` DTO SHALL include a nested `costs: EpisodeCostsResponse` object containing per-stage rows (`score`, `dedup`, `compose`, `recap`), a TTS row, a research row, and a `totalCostCents` field. Each LLM stage row SHALL carry `{ model, calls, inputTokens, outputTokens, costCents }`. The TTS row SHALL carry `{ model, characters, costCents }`. The research row SHALL carry `{ calls, costCents }`. The `model` field SHALL be sourced from the episode's persisted `filterModel` (for score/dedup/recap) or `composeModel` (for compose) columns. The `calls` field SHALL be the count of articles linked to the episode for the score row, and 1 if any token/cost is present (else 0) for dedup/compose/recap. The `totalCostCents` SHALL be the sum of all six rows' cost cents.

#### Scenario: Costs object present on episode GET
- **WHEN** `GET /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}` is called
- **THEN** the response includes a `costs` field with score/dedup/compose/recap/tts/research children and a `totalCostCents`

#### Scenario: Score row reflects article count
- **WHEN** an episode has 41 articles linked
- **THEN** `costs.score.calls` is 41

#### Scenario: LLM stage row reflects single call when stage ran
- **WHEN** dedup, compose, or recap stage has any non-zero input tokens, output tokens, or cost cents
- **THEN** that row's `calls` field is 1, else 0

#### Scenario: Legacy episode shows zero costs but real token counts
- **WHEN** a pre-V57 episode is returned via the API
- **THEN** `costs.score` reflects backfilled token counts but `costCents = 0`; dedup/compose/recap rows are zero across the board; tts and research rows reflect the existing persisted values

