## ADDED Requirements

### Requirement: Provider-reported LLM cost captured from the response
The system SHALL capture the provider's own reported cost for an LLM call when the provider supplies one. OpenRouter returns `cost` (USD) inside the usage object, which Spring AI exposes through the native usage's additional properties. `TokenUsage` SHALL carry this value as a nullable USD amount alongside the existing input and output token counts.

`TokenUsage.fromChatResponse` SHALL resolve the reported cost in this order: a cost carried on the `ChatResponseMetadata` (present only when the response was replayed from the LLM cache), then the `cost` entry in the native usage's additional properties, then none. A value that is absent, non-numeric, or negative SHALL be treated as no reported cost. Extraction failures SHALL NOT propagate; they SHALL yield no reported cost so the caller falls back to configured rates.

Providers that report no cost, including the direct `openai` provider, SHALL yield no reported cost and SHALL continue to be costed from configured rates.

#### Scenario: OpenRouter cost captured from a live call
- **WHEN** an LLM call is made through OpenRouter and the usage object contains `cost: 7.6E-5`
- **THEN** the resulting `TokenUsage` carries a reported cost of `7.6E-5` USD alongside the token counts

#### Scenario: Provider reports no cost
- **WHEN** an LLM call is made through a provider whose usage object has no `cost` entry
- **THEN** the resulting `TokenUsage` carries no reported cost and the token counts are unchanged

#### Scenario: Malformed cost value ignored
- **WHEN** the usage object contains a `cost` entry that is non-numeric or negative
- **THEN** the resulting `TokenUsage` carries no reported cost and no exception propagates to the pipeline

#### Scenario: Token counts unaffected by cost extraction
- **WHEN** a response carries usage metadata
- **THEN** `inputTokens` and `outputTokens` are read exactly as before, whether or not a reported cost is present

### Requirement: LLM cost resolved from the provider before the pricing table
The system SHALL resolve an LLM call's cost in this precedence: the provider-reported cost when present, otherwise the configured `input-cost-per-mtok` / `output-cost-per-mtok` rates for the model, otherwise unknown. The resolution SHALL happen in `CostEstimator` so the precedence is defined in one place rather than at each call site.

The resolution SHALL also yield a cost source of `API` (reported by the provider on a live call), `API_CACHED` (a reported cost replayed from the LLM cache), `TABLE` (computed from configured rates), or `UNKNOWN` (neither available).

Persisted `*_cost_cents` columns and the LLM budget gate SHALL continue to use rounded integer cents. A reported cost SHALL be usable at full precision through the exact-value path so that sub-cent costs stay visible, matching the existing treatment of sub-cent token-derived costs.

#### Scenario: Reported cost preferred over configured rates
- **WHEN** a call reports a cost of `0.00042` USD and the model also has configured per-Mtok rates
- **THEN** the resolved cost is `0.042` cents from the reported value, not the value computed from tokens, and the source is `API`

#### Scenario: Falls back to configured rates when nothing is reported
- **WHEN** a call reports no cost and the model has configured per-Mtok rates
- **THEN** the resolved cost is computed from tokens and rates as before, and the source is `TABLE`

#### Scenario: Unknown when neither is available
- **WHEN** a call reports no cost and the model has no configured rates
- **THEN** the resolved cost is null and the source is `UNKNOWN`

#### Scenario: Sub-cent reported cost survives at full precision
- **WHEN** a call reports a cost of `7.6E-5` USD (0.0076 cents)
- **THEN** the exact-value path returns `0.0076` cents rather than rounding it away, while the persisted integer-cent value rounds to 0

### Requirement: Reported cost cached and replayed with the cached response
The `llm_cache` table SHALL store the provider-reported cost alongside the cached response and its token counts. `CachingChatModel` SHALL persist the reported cost on a cache miss and SHALL replay it on a cache hit by attaching it to the reconstructed response's metadata.

Replaying preserves existing semantics: the cache already replays token counts, so a cached call is already costed as though it ran. A cache hit SHALL therefore carry the original call's cost rather than zero, and the LLM budget gate's observable behaviour SHALL be unchanged. A replayed cost SHALL be recorded with source `API_CACHED` so it is distinguishable from a fresh charge.

Cache rows written before this capability existed have no stored reported cost; on a hit they SHALL replay token counts only and resolve to source `TABLE`.

#### Scenario: Reported cost stored on a cache miss
- **WHEN** an LLM call misses the cache, succeeds, and reports a cost
- **THEN** the stored `llm_cache` row records that cost alongside the response and token counts

#### Scenario: Reported cost replayed on a cache hit
- **WHEN** an LLM call hits a cache row that has a stored reported cost
- **THEN** the reconstructed response carries that cost, the resolved cost equals the original call's cost, and the source is `API_CACHED`

#### Scenario: Legacy cache row without a reported cost
- **WHEN** an LLM call hits a cache row stored before this capability existed
- **THEN** the reconstructed response carries token counts only and the cost resolves from configured rates with source `TABLE`

#### Scenario: Blank responses still not cached
- **WHEN** a call returns a blank completion
- **THEN** nothing is cached, unchanged by this capability

### Requirement: Stage cost aggregated across calls with gaps estimated
A pipeline stage MAY make more than one LLM call; the scoring stage makes one call per article. When a stage's calls do not all report a cost, the stage total SHALL be the sum of the reported costs plus a table estimate, computed from their own token counts and the model's configured rate, for each call that reported nothing. The stage SHALL be recorded as `MIXED`.

The system SHALL NOT present the sum of only the reported costs as the stage total when some calls reported nothing, because a partial sum is indistinguishable from a complete one and would be honoured over the configured rates, silently understating the stage.

When every call in a stage reported a cost, the stage total SHALL be their sum with source `API`. When no call reported a cost, the stage total SHALL be computed from the summed tokens and the configured rate with source `TABLE`, unchanged from current behaviour.

Summing reported costs is permitted where summing per-article `llm_cost_cents` is not: the existing requirement to recompute score cost from summed tokens exists because integer cents round sub-cent calls to zero, whereas reported costs are full-precision USD values and lose nothing when added.

To support this, the per-article reported cost SHALL be persisted alongside the existing per-article token counts.

#### Scenario: All calls in a stage reported
- **WHEN** an episode's scoring stage runs 40 article calls and every one reports a cost
- **THEN** the stage cost is the sum of those 40 reported costs and the stage source is `API`

#### Scenario: Some calls reported and some did not
- **WHEN** an episode's scoring stage runs 40 article calls, 38 report a cost totalling `$0.0152`, and 2 report nothing but together used 900 input and 300 output tokens
- **THEN** the stage cost is `$0.0152` plus the configured-rate estimate for those 900 and 300 tokens, and the stage source is `MIXED`

#### Scenario: No call in a stage reported
- **WHEN** none of a stage's calls report a cost and the model has configured rates
- **THEN** the stage cost is computed from the summed tokens and the configured rate, and the stage source is `TABLE`

#### Scenario: Partial sum never presented as complete
- **WHEN** a stage has both reporting and non-reporting calls
- **THEN** the stage total is never the bare sum of the reported costs alone, and the stage is never recorded as `API`

#### Scenario: Gap estimate impossible without rates
- **WHEN** a stage has non-reporting calls and the model has no configured rates
- **THEN** the non-reporting calls contribute nothing to the total and the stage source is `MIXED`, so the shortfall is visible rather than implied to be complete

### Requirement: Episode records where its LLM cost came from
Each episode SHALL persist the source of its LLM cost in a nullable `llm_cost_source` column with values `API`, `API_CACHED`, `TABLE`, `MIXED`, or `UNKNOWN`. The value SHALL be `API` when every contributing stage resolved from a provider-reported cost, `TABLE` when none did, `MIXED` when some did and some did not, and `UNKNOWN` when no cost could be determined for any stage. A run in which every contributing stage was a cache replay SHALL record `API_CACHED`.

Episodes generated before this column existed SHALL have a null source, which SHALL be presented as an estimate.

#### Scenario: All stages report a cost
- **WHEN** every LLM stage of an episode runs through OpenRouter and reports a cost
- **THEN** the episode's `llm_cost_source` is `API`

#### Scenario: Mixed sources across stages
- **WHEN** an episode's compose stage runs through OpenRouter and reports a cost while its filter stage runs through the direct `openai` provider and does not
- **THEN** the episode's `llm_cost_source` is `MIXED`

#### Scenario: No stage reports a cost
- **WHEN** no LLM stage of an episode reports a cost and all stages have configured rates
- **THEN** the episode's `llm_cost_source` is `TABLE`

#### Scenario: Legacy episode has no source
- **WHEN** an episode generated before this column existed is read
- **THEN** its `llm_cost_source` is null and it is presented as an estimate

### Requirement: Migration adds the reported-cost columns
Migration `V64` SHALL add a nullable reported-cost column to `llm_cache`, a nullable reported-cost column to `articles`, and a nullable `llm_cost_source` column to `episodes`. All SHALL be nullable with no default so that existing rows are distinguishable from rows written after the change. No data SHALL be backfilled: a reported cost that was never captured cannot be reconstructed.

#### Scenario: Migration applies to an existing database
- **WHEN** `V64` runs against a database containing existing episodes, articles and cache rows
- **THEN** the new columns are added, existing rows carry null in all of them, and no existing values are modified

#### Scenario: Existing costs remain readable
- **WHEN** an episode created before `V64` is read after the migration
- **THEN** its persisted `*_cost_cents` values are unchanged and its cost source is null

## MODIFIED Requirements

### Requirement: Episode cost breakdown surfaced in API
The `EpisodeResponse` DTO SHALL include a nested `costs: EpisodeCostsResponse` object containing per-stage rows (`score`, `dedup`, `compose`, `recap`), a TTS row, a research row, and a `totalCostCents` field. Each LLM stage row SHALL carry `{ model, calls, inputTokens, outputTokens, costCents }`. The TTS row SHALL carry `{ model, characters, costCents }`. The research row SHALL carry `{ calls, costCents }`. The `model` field SHALL be sourced from the episode's persisted `filterModel` (for score/dedup/recap) or `composeModel` (for compose) columns. The `calls` field SHALL be the count of articles linked to the episode for the score row, and 1 if any token/cost is present (else 0) for dedup/compose/recap.

The `costs` object SHALL additionally expose the episode's `costSource`, reflecting the persisted `llm_cost_source` and null for episodes generated before that column existed, so a client can distinguish an actual charge from an estimate.

All `costCents` fields and `totalCostCents` SHALL be fractional cents (`Double`), not integer cents, so that sub-cent stage costs from cheap models stay visible. For each LLM stage row, `costCents` SHALL be the persisted provider-reported cost when one was captured for that stage. When no reported cost is available, `costCents` SHALL be recomputed from the persisted token totals and the model's configured rate at full precision via `CostEstimator.estimateLlmCostCentsExact`. When the model rate is also unknown, or the stage has zero tokens, the row SHALL fall back to the persisted integer-cent value coerced to `Double`. The TTS and research rows SHALL carry their persisted integer-cent values coerced to `Double`. The `totalCostCents` SHALL be the sum of all six rows' cost cents. Persisted columns (`*_cost_cents`, aggregate `llm_cost_cents`) and the LLM cost gate are unaffected and continue to use rounded integer cents.

#### Scenario: Costs object present on episode GET
- **WHEN** `GET /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}` is called
- **THEN** the response includes a `costs` field with score/dedup/compose/recap/tts/research children, a `totalCostCents`, and a `costSource`

#### Scenario: Score row reflects article count
- **WHEN** an episode has 41 articles linked
- **THEN** `costs.score.calls` is 41

#### Scenario: LLM stage row reflects single call when stage ran
- **WHEN** dedup, compose, or recap stage has any non-zero input tokens, output tokens, or cost cents
- **THEN** that row's `calls` field is 1, else 0

#### Scenario: Reported cost preferred over recomputation
- **WHEN** a stage captured a provider-reported cost of `0.0076` cents and the model also has configured rates
- **THEN** that row's `costCents` is `0.0076`, not the value recomputed from tokens and rates

#### Scenario: Sub-cent stage cost stays visible for cheap model
- **WHEN** an episode's scoring stage used `deepseek/deepseek-v4-flash` with 4785 input and 1899 output tokens, no reported cost, and persisted `score_cost_cents` rounded to 0
- **THEN** `costs.score.costCents` is the full-precision value (~0.084 cents) recomputed from tokens, not 0

#### Scenario: Stage with unknown model rate falls back to persisted cents
- **WHEN** a stage has no reported cost, its model is not present in `app.models`, and the persisted stage cost is 3 cents
- **THEN** that row's `costCents` is 3.0

#### Scenario: Cost source exposed for a reported-cost episode
- **WHEN** an episode whose stages all reported a cost is returned via the API
- **THEN** `costs.costSource` is `API`

#### Scenario: Legacy episode shows zero costs but real token counts
- **WHEN** a pre-V57 episode is returned via the API
- **THEN** `costs.score` reflects backfilled token counts; its `costCents` is recomputed from tokens when the model rate is known, otherwise 0.0; dedup/compose/recap rows are zero across the board; tts and research rows reflect the existing persisted values; and `costs.costSource` is null
