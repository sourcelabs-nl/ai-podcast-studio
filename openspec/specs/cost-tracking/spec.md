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
The `EpisodeResponse` DTO SHALL include a nested `costs: EpisodeCostsResponse` object containing per-stage rows (`score`, `dedup`, `compose`, `recap`), a TTS row, a research row, and a `totalCostCents` field. Each LLM stage row SHALL carry `{ model, calls, inputTokens, outputTokens, costCents }`. The TTS row SHALL carry `{ model, characters, costCents }`. The research row SHALL carry `{ calls, costCents }`. The `model` field SHALL be sourced from the episode's persisted `filterModel` (for score/dedup/recap) or `composeModel` (for compose) columns. The `calls` field SHALL be the count of articles linked to the episode for the score row, and 1 if any token/cost is present (else 0) for dedup/compose/recap.

The `costs` object SHALL additionally expose the episode's `costSource`, reflecting the persisted `llm_cost_source` and null for episodes generated before that column existed, so a client can distinguish an actual charge from an estimate.

All `costCents` fields and `totalCostCents` SHALL be fractional cents (`Double`), not integer cents, so that sub-cent stage costs from cheap models stay visible. For each of the four LLM stage rows, `costCents` SHALL be that stage's persisted per-stage reported cost when one was captured. When no reported cost is available, `costCents` SHALL be recomputed from the persisted token totals and the model's configured rate at full precision via `CostEstimator.estimateLlmCostCentsExact`. When the model rate is also unknown, or the stage has zero tokens, the row SHALL fall back to the persisted integer-cent value coerced to `Double`. The TTS and research rows SHALL carry their persisted integer-cent values coerced to `Double`. The `totalCostCents` SHALL be the sum of all six rows' cost cents. Persisted columns (`*_cost_cents`, aggregate `llm_cost_cents`) and the LLM cost gate are unaffected and continue to use rounded integer cents.

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
- **WHEN** any of the score, dedup, compose or recap stages captured a provider-reported cost and its model also has configured rates
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

#### Scenario: Legacy episode shows zero costs but real token counts
- **WHEN** a pre-V57 episode is returned via the API
- **THEN** all four per-stage reported costs are null; `costs.score` reflects backfilled token counts; its `costCents` is recomputed from tokens when the model rate is known, otherwise 0.0; dedup/compose/recap rows are zero across the board; tts and research rows reflect the existing persisted values; and `costs.costSource` is null

### Requirement: TTS synthesis call count tracked and exposed
The system SHALL persist the number of TTS synthesis API calls made for an episode in a nullable `tts_calls` column on `episodes`. TTS synthesis splits the script into chunks (one API call per chunk, concatenated afterwards), so the call count SHALL equal the number of synthesized audio chunks (`ttsResult.audioChunks.size`). The value SHALL be written on both fresh generation and audio regeneration. The per-episode cost breakdown response SHALL expose this count as `costs.tts.calls`, defaulting to 0 when the column is null (legacy episodes generated before this column existed). TTS cost SHALL remain character-based and unaffected by the call count.

#### Scenario: Call count recorded for a multi-chunk episode
- **WHEN** an episode's script is synthesized in 7 chunks
- **THEN** the episode's `tts_calls` is 7 and the cost breakdown response has `costs.tts.calls = 7`

#### Scenario: Legacy episode without recorded call count
- **WHEN** an episode predates the `tts_calls` column and its value is null
- **THEN** the cost breakdown response has `costs.tts.calls = 0` (rendered as `—` in the UI)

#### Scenario: Call count does not affect TTS cost
- **WHEN** an episode of 10000 characters is synthesized in either 1 or 5 calls
- **THEN** the TTS cost is identical, computed only from the character total and `cost-per-million-chars`

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

### Requirement: Episode persists each stage's reported cost
Each episode SHALL persist a per-stage provider-reported cost, in fractional cents, for the score, dedup, compose and recap stages. A stage's value SHALL be its full-precision resolved cost when, and only when, a provider-reported value contributed to it: sources `API`, `API_CACHED` and `MIXED`. Sources `TABLE` and `UNKNOWN` SHALL persist null, so those stages are recomputed from tokens and the configured rates on the read path.

A `MIXED` stage's persisted value includes the configured-rate estimate for the calls that reported nothing. It is still persisted as a reported cost because it is closer to the actual charge than recomputing the whole stage from rates, and the episode's `llm_cost_source` already marks such a total `MIXED`.

The rule deciding whether a resolved cost counts as reported SHALL live in one place on the resolved-cost type, rather than being repeated at each stage's call site.

The values SHALL be written through the same `EpisodeService` paths that already maintain the per-stage `*_cost_cents` columns and the `llm_cost_source` aggregate, so they cannot drift from them.

#### Scenario: Stage resolved from a provider-reported cost
- **WHEN** an episode's compose stage resolves its cost from a provider-reported value with source `API`
- **THEN** the episode's `compose_reported_cost_cents` is that full-precision value in fractional cents

#### Scenario: Cache-replayed stage persists its reported cost
- **WHEN** an episode's dedup stage replays a cached reported cost with source `API_CACHED`
- **THEN** the episode's `dedup_reported_cost_cents` is that value

#### Scenario: Partially reported stage persists its total
- **WHEN** an episode's score stage resolves to `MIXED`, summing reported per-article costs and a configured-rate estimate for the articles that reported nothing
- **THEN** the episode's `score_reported_cost_cents` is that combined total and the episode's `llm_cost_source` reflects `MIXED`

#### Scenario: Estimated stage persists no reported cost
- **WHEN** an episode's recap stage resolves from the configured rates with source `TABLE`, or resolves to `UNKNOWN`
- **THEN** the episode's `recap_reported_cost_cents` is null

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
Migration `V64` SHALL add a nullable reported-cost column to `llm_cache`, a nullable reported-cost column to `articles`, a nullable `llm_cost_source` column to `episodes`, and four nullable per-stage reported-cost columns to `episodes` (`score_reported_cost_cents`, `dedup_reported_cost_cents`, `compose_reported_cost_cents`, `recap_reported_cost_cents`). All SHALL be nullable with no default so that existing rows are distinguishable from rows written after the change. No data SHALL be backfilled: a reported cost that was never captured cannot be reconstructed.

The `llm_cache` and `articles` columns SHALL hold USD, matching the unit the provider reports. The four per-stage episode columns SHALL hold fractional cents, matching the unit the cost breakdown serves, so a stored value needs no second conversion on the read path.

#### Scenario: Migration applies to an existing database
- **WHEN** `V64` runs against a database containing existing episodes, articles and cache rows
- **THEN** the new columns are added, existing rows carry null in all of them, and no existing values are modified

#### Scenario: Existing costs remain readable
- **WHEN** an episode created before `V64` is read after the migration
- **THEN** its persisted `*_cost_cents` values are unchanged and its cost source and all four per-stage reported costs are null

### Requirement: LLM request timeouts are set per pipeline stage
The system SHALL apply a request timeout to each LLM call based on the pipeline stage the model was resolved for, rather than one value shared by every stage. `ResolvedModel` SHALL carry its `PipelineStage`, and `ChatClientFactory` SHALL select the timeout from it.

Timeouts SHALL be configurable under `app.llm.timeouts`, defaulting to 3 minutes for `filter`, 5 minutes for `dedup` and 20 minutes for `compose`. The recap stage resolves the filter model and SHALL therefore take the filter timeout.

The values reflect measured behaviour. Per-article scoring returns in seconds and dedup has run to a maximum of 3 minutes 6 seconds, so neither needs more than a few minutes. Composition, however, has been observed between 1 minute 3 seconds and 18 minutes 11 seconds when working over a large article set with research and history tool calls, so its ceiling SHALL remain generous: a 15-minute ceiling would have failed a real episode.

A single blanket 20-minute timeout previously let one hung scoring call stall an entire generation for 13 minutes while the other 177 articles had each returned in seconds.

#### Scenario: A hung scoring call is cut off in minutes, not tens of minutes
- **WHEN** an article-scoring request stops responding
- **THEN** it fails after the filter timeout (3 minutes by default), not after the compose timeout

#### Scenario: A long composition is not cut off
- **WHEN** a compose request with research and history tool calls runs for 18 minutes
- **THEN** it is allowed to complete, because the compose timeout is 20 minutes

#### Scenario: Dedup takes the dedup timeout
- **WHEN** the dedup stage issues its LLM call
- **THEN** the request timeout is the configured dedup value, independent of the compose value

#### Scenario: Recap takes the filter timeout
- **WHEN** the recap stage issues its LLM call, having resolved the filter model
- **THEN** the request timeout is the configured filter value

### Requirement: Compose bounds its output tokens
The composers (`BriefingComposer`, `DialogueComposer`, `InterviewComposer`) SHALL set a `maxTokens` ceiling on the compose request, configured as `app.compose.max-output-tokens` (default 96000).

Without a ceiling the provider reserves the model's entire output window when checking affordability, which blocked episode 191 with a 402 demanding credit for 131,072 tokens to produce a 1,876-word script. The ceiling SHALL leave ample headroom over real usage: compose output across 59 episodes ranged from 2,540 to 57,546 tokens, the upper end reflecting a reasoning model whose output greatly exceeds the script it returns. A ceiling below observed usage would truncate a script, which is why the default sits well above the maximum seen rather than close to it.

#### Scenario: Compose request declares an output ceiling
- **WHEN** any composer issues its compose request
- **THEN** the request carries `maxTokens` equal to `app.compose.max-output-tokens`

#### Scenario: Ceiling leaves headroom over observed usage
- **WHEN** the default ceiling is applied
- **THEN** it exceeds the largest observed compose output (57,546 tokens) by a wide margin and stays below the model's 131,072-token window
