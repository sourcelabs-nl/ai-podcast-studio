## MODIFIED Requirements

### Requirement: Episode cost breakdown surfaced in API
The `EpisodeResponse` DTO SHALL include a nested `costs: EpisodeCostsResponse` object containing per-stage rows (`score`, `dedup`, `compose`, `recap`), a TTS row, a research row, and a `totalCostCents` field. Each LLM stage row SHALL carry `{ model, calls, inputTokens, outputTokens, costCents }`. The TTS row SHALL carry `{ model, characters, costCents }`. The research row SHALL carry `{ calls, costCents }`. The `model` field SHALL be sourced from the episode's persisted `filterModel` (for score/dedup/recap) or `composeModel` (for compose) columns. The `calls` field SHALL be the count of articles linked to the episode for the score row, and 1 if any token/cost is present (else 0) for dedup/compose/recap.

All `costCents` fields and `totalCostCents` SHALL be fractional cents (`Double`), not integer cents, so that sub-cent stage costs from cheap models stay visible. For each LLM stage row, `costCents` SHALL be recomputed from the persisted token totals and the model's configured rate at full precision via `CostEstimator.estimateLlmCostCentsExact`. When the model rate is unknown (model absent from `app.models`) or the stage has zero tokens, the row SHALL fall back to the persisted integer-cent value coerced to `Double`. The TTS and research rows SHALL carry their persisted integer-cent values coerced to `Double`. The `totalCostCents` SHALL be the sum of all six rows' cost cents. Persisted columns (`*_cost_cents`, aggregate `llm_cost_cents`) and the LLM cost gate are unaffected and continue to use rounded integer cents.

#### Scenario: Costs object present on episode GET
- **WHEN** `GET /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}` is called
- **THEN** the response includes a `costs` field with score/dedup/compose/recap/tts/research children and a `totalCostCents`

#### Scenario: Score row reflects article count
- **WHEN** an episode has 41 articles linked
- **THEN** `costs.score.calls` is 41

#### Scenario: LLM stage row reflects single call when stage ran
- **WHEN** dedup, compose, or recap stage has any non-zero input tokens, output tokens, or cost cents
- **THEN** that row's `calls` field is 1, else 0

#### Scenario: Sub-cent stage cost stays visible for cheap model
- **WHEN** an episode's scoring stage used `deepseek/deepseek-v4-flash` with 4785 input and 1899 output tokens (persisted `score_cost_cents` rounded to 0)
- **THEN** `costs.score.costCents` is the full-precision value (~0.084 cents) recomputed from tokens, not 0

#### Scenario: Stage with unknown model rate falls back to persisted cents
- **WHEN** a stage's model is not present in `app.models` and the persisted stage cost is 3 cents
- **THEN** that row's `costCents` is 3.0

#### Scenario: Legacy episode shows zero costs but real token counts
- **WHEN** a pre-V57 episode is returned via the API
- **THEN** `costs.score` reflects backfilled token counts; its `costCents` is recomputed from tokens when the model rate is known, otherwise 0.0; dedup/compose/recap rows are zero across the board; tts and research rows reflect the existing persisted values
