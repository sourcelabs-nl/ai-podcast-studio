## MODIFIED Requirements

### Requirement: Per-stage episode LLM cost breakdown
The system SHALL persist per-stage LLM token and cost totals on each episode, split across five stages: scoring (Stage 1), dedup (Stage 3), the dedup stage's already-covered gate, compose (Stage 4), and recap. The columns SHALL be `score_input_tokens`, `score_output_tokens`, `score_cost_cents`, `dedup_input_tokens`, `dedup_output_tokens`, `dedup_cost_cents`, `dedup_gate_input_tokens`, `dedup_gate_output_tokens`, `dedup_gate_cost_cents`, `dedup_gate_calls`, `compose_input_tokens`, `compose_output_tokens`, `compose_cost_cents`, `recap_input_tokens`, `recap_output_tokens`, `recap_cost_cents` (all INTEGER NOT NULL DEFAULT 0). The pre-existing aggregate columns `llm_input_tokens`, `llm_output_tokens`, `llm_cost_cents` SHALL continue to exist but SHALL be maintained as the sum of the five per-stage triples and written through a single code path in `EpisodeService` — no other code path may update them independently.

The gate SHALL carry a persisted call count of its own, because unlike the other single-call stages its request count is not derivable from its tokens: it issues one request per evaluation and each retried attempt is a further request.

#### Scenario: Per-stage triples written when each stage runs
- **WHEN** an episode is generated end-to-end with all five LLM stages
- **THEN** `score_*`, `dedup_*`, `dedup_gate_*`, `compose_*`, `recap_*` triples are each populated with the tokens and cost cents of their respective stage

#### Scenario: Aggregate equals sum of stages
- **WHEN** any LLM stage persists its triple
- **THEN** `llm_input_tokens`, `llm_output_tokens`, `llm_cost_cents` are recomputed as the sum of the five corresponding stage columns, in the same transaction

#### Scenario: Stages that did not run carry zero
- **WHEN** a pipeline run skips a stage (e.g. recap fails and is swallowed, or the gate is not configured)
- **THEN** the corresponding stage triple stays at 0 and the aggregate sums the stages that did run

#### Scenario: The gate's charge no longer lands on the dedup stage
- **WHEN** the dedup stage runs with the gate enabled and both report a cost
- **THEN** `dedup_cost_cents` holds the clustering call's cost alone and `dedup_gate_cost_cents` holds the gate's, and the aggregate is unchanged from what it was when the two were added together

### Requirement: Score stage cost computed from token totals
The system SHALL compute `score_cost_cents` from the SUM of scoring tokens across every article recorded as a candidate for the episode, whether or not it reached the script, using `CostEstimator.estimateLlmCostCents(totalScoreInput, totalScoreOutput, filterModel.cost)`. The system SHALL NOT compute `score_cost_cents` by summing `articles.llm_cost_cents`, because per-article integer cents round sub-cent calls to 0 and lose precision.

An article is scored against its full body, which is the most expensive input the pipeline sends, and it is scored because it fell in this episode's window. Costing the stage over the survivors alone attributes that spend to nothing.

#### Scenario: Dropped candidates are charged to the episode
- **WHEN** an episode scores 188 candidates and composes 121 of them
- **THEN** `score_cost_cents` reflects the tokens of all 188, not of the 121

#### Scenario: Score cost preserved despite per-article zero costs
- **WHEN** an episode is generated with 40 articles each costing $0.000123 to score (per-article `llm_cost_cents` rounds to 0)
- **THEN** `score_cost_cents` reflects the total cost ($0.0049 → 0¢ or 1¢ depending on rounding of the SUM), not 0 from summing rounded zeros

#### Scenario: Score cost null-coerced when pricing missing
- **WHEN** the filter model has no pricing configured in `app.models`
- **THEN** `score_cost_cents` is 0 (CostEstimator returns null, coerced at the persistence boundary)

### Requirement: Episode cost breakdown surfaced in API
The `EpisodeResponse` DTO SHALL include a nested `costs: EpisodeCostsResponse` object containing per-stage rows (`score`, `dedup`, `dedupGate`, `compose`, `recap`), a TTS row, a research row, and a `totalCostCents` field. Each LLM stage row SHALL carry `{ model, calls, inputTokens, outputTokens, costCents }`. The TTS row SHALL carry `{ model, characters, costCents }`. The research row SHALL carry `{ calls, costCents }`. The `model` field SHALL be sourced from the episode's persisted `filterModel` (for score/dedup/recap) or `composeModel` (for compose) columns, and from the configured gate model for `dedupGate`. The `calls` field SHALL be the count of articles recorded as candidates for the episode for the score row, the persisted call count for `dedupGate`, and 1 if any token/cost is present (else 0) for dedup/compose/recap.

The score row SHALL additionally carry `droppedCalls` and `droppedCostCents`, describing the part of that same row accounted for by candidates that did not reach the script. These are a breakdown of the score row, not a row of their own, and SHALL NOT be added to `totalCostCents` again. They SHALL be computed on the read path from the episode's candidate records and the token counts on the articles they name.

The `costs` object SHALL additionally expose the episode's `costSource`, reflecting the persisted `llm_cost_source` and null for episodes generated before that column existed, so a client can distinguish an actual charge from an estimate.

All `costCents` fields and `totalCostCents` SHALL be fractional cents (`Double`), not integer cents, so that sub-cent stage costs from cheap models stay visible. For each of the five LLM stage rows, `costCents` SHALL be that stage's persisted per-stage reported cost when one was captured. When no reported cost is available, `costCents` SHALL be recomputed from the persisted token totals and the model's configured rate at full precision via `CostEstimator.estimateLlmCostCentsExact`. When the model rate is also unknown, or the stage has zero tokens, the row SHALL fall back to the persisted integer-cent value coerced to `Double`. The TTS and research rows SHALL carry their persisted integer-cent values coerced to `Double`. The `totalCostCents` SHALL be the sum of all seven rows' cost cents. Persisted columns (`*_cost_cents`, aggregate `llm_cost_cents`) and the LLM cost gate are unaffected and continue to use rounded integer cents.

#### Scenario: Costs object present on episode GET
- **WHEN** `GET /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}` is called
- **THEN** the response includes a `costs` field with score/dedup/dedupGate/compose/recap/tts/research children, a `totalCostCents`, and a `costSource`

#### Scenario: Score row reflects article count
- **WHEN** an episode scored 188 candidates and linked 121 articles
- **THEN** `costs.score.calls` is 188 and `costs.score.droppedCalls` is 67

#### Scenario: Dropped breakdown is not double-counted
- **WHEN** the score row carries a non-zero `droppedCostCents`
- **THEN** `totalCostCents` counts the score row once, and adding `droppedCostCents` to it would overstate the episode

#### Scenario: Gate row reports its own requests
- **WHEN** an episode's gate issued one request that succeeded after one retried failure
- **THEN** `costs.dedupGate.calls` is 2 and `costs.dedup.calls` is 1

#### Scenario: LLM stage row reflects single call when stage ran
- **WHEN** dedup, compose, or recap stage has any non-zero input tokens, output tokens, or cost cents
- **THEN** that row's `calls` field is 1, else 0

#### Scenario: Reported cost preferred over recomputation
- **WHEN** any of the score, dedup, dedup gate, compose or recap stages captured a provider-reported cost and its model also has configured rates
- **THEN** that row's `costCents` is the persisted reported value, not the value recomputed from tokens and rates

#### Scenario: Stage without a reported cost is recomputed
- **WHEN** a stage has a null persisted reported cost, non-zero tokens, and a known model rate
- **THEN** that row's `costCents` is recomputed from its tokens and rate at full precision

#### Scenario: Sub-cent stage cost stays visible for cheap model
- **WHEN** an episode's scoring stage used `deepseek/deepseek-v4-flash` with 4785 input and 1899 output tokens, no reported cost, and persisted `score_cost_cents` rounded to 0
- **THEN** `costs.score.costCents` is the full-precision value (~0.084 cents) recomputed from tokens, not 0

#### Scenario: Stage with unknown model rate falls back to persisted cents
- **WHEN** a stage has a null persisted reported cost, its model is not present in `app.models`, and the persisted stage cost is 3 cents
- **THEN** that row's `costCents` is 3.0

#### Scenario: Cost source exposed for a reported-cost episode
- **WHEN** an episode whose stages all reported a cost is returned via the API
- **THEN** `costs.costSource` is `API`

#### Scenario: An episode generated before candidates were recorded
- **WHEN** an episode that recorded no candidates is returned via the API
- **THEN** `costs.score.calls` is the number of articles linked to it, `droppedCalls` is 0 and `droppedCostCents` is 0, and its `costs.dedupGate` row is zero across the board while `costs.dedup` keeps the amount it was stored with

#### Scenario: Legacy episode shows zero costs but real token counts
- **WHEN** a pre-V57 episode is returned via the API
- **THEN** all per-stage reported costs are null; `costs.score` reflects backfilled token counts; its `costCents` is recomputed from tokens when the model rate is known, otherwise 0.0; dedup/dedup gate/compose/recap rows are zero across the board; tts and research rows reflect the existing persisted values; and `costs.costSource` is null

### Requirement: Episode persists each stage's reported cost
Each episode SHALL persist a per-stage provider-reported cost, in fractional cents, for the score, dedup, dedup gate, compose and recap stages. A stage's value SHALL be its full-precision resolved cost when, and only when, a provider-reported value contributed to it: sources `API`, `API_CACHED` and `MIXED`. Sources `TABLE` and `UNKNOWN` SHALL persist null, so those stages are recomputed from tokens and the configured rates on the read path.

A `MIXED` stage's persisted value includes the configured-rate estimate for the calls that reported nothing. It is still persisted as a reported cost because it is closer to the actual charge than recomputing the whole stage from rates, and the episode's `llm_cost_source` already marks such a total `MIXED`.

The rule deciding whether a resolved cost counts as reported SHALL live in one place on the resolved-cost type, rather than being repeated at each stage's call site.

The values SHALL be written through the same `EpisodeService` paths that already maintain the per-stage `*_cost_cents` columns and the `llm_cost_source` aggregate, so they cannot drift from them.

#### Scenario: Stage resolved from a provider-reported cost
- **WHEN** an episode's compose stage resolves its cost from a provider-reported value with source `API`
- **THEN** the episode's `compose_reported_cost_cents` is that full-precision value in fractional cents

#### Scenario: The gate's reported charge is persisted as its own
- **WHEN** the gate's request reports a cost
- **THEN** that value is persisted as `dedup_gate_reported_cost_cents` and does not contribute to `dedup_reported_cost_cents`

#### Scenario: Cache-replayed stage persists its reported cost
- **WHEN** an episode's dedup stage replays a cached reported cost with source `API_CACHED`
- **THEN** the episode's `dedup_reported_cost_cents` is that value

#### Scenario: Partially reported stage persists its total
- **WHEN** an episode's score stage resolves to `MIXED`, summing reported per-article costs and a configured-rate estimate for the articles that reported nothing
- **THEN** the episode's `score_reported_cost_cents` is that combined total and the episode's `llm_cost_source` reflects `MIXED`

#### Scenario: Estimated stage persists no reported cost
- **WHEN** an episode's recap stage resolves from the configured rates with source `TABLE`, or resolves to `UNKNOWN`
- **THEN** the episode's `recap_reported_cost_cents` is null

## ADDED Requirements

### Requirement: The dedup gate is costed as a stage of its own
The already-covered gate's cost SHALL be reported as a stage beside the dedup stage rather than added to it. The gate runs a different model at different rates against a different question, and folding its charge into the dedup amount leaves it indistinguishable from the clustering call it relieves.

The two SHALL carry the same stage names the recorded requests already use, `dedup` and `dedup-gate`, so an amount in the costs breakdown and a set of percentiles in the latency report describe the same thing.

#### Scenario: Gate cost separated from the clustering call
- **WHEN** an episode is generated with the gate enabled
- **THEN** the costs breakdown reports the gate's tokens, requests and cost under its own stage, and the dedup stage reports the clustering call alone

#### Scenario: Gate disabled
- **WHEN** an episode is generated with no gate configured
- **THEN** the gate's stage is reported with zero requests and zero cost, and no cost is attributed to it
