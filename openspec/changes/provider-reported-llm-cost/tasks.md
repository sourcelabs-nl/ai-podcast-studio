## 1. Capture the reported cost

- [x] 1.1 Add a nullable `reportedCostUsd: Double?` to `TokenUsage`, defaulting to null so existing construction sites compile unchanged
- [x] 1.2 Extract the cost in `TokenUsage.fromChatResponse` from `nativeUsage._additionalProperties()["cost"]`, wrapped so any shape change or conversion failure yields null instead of throwing
- [x] 1.3 Treat absent, non-numeric and negative values as no reported cost
- [x] 1.4 Resolve a metadata-carried cost first, so a cache replay takes precedence over native usage extraction
- [x] 1.5 Delete `TokenUsage.plus` and `TokenUsage.ZERO`, which are unreferenced in both `src/main` and `src/test`; aggregation belongs at the stage level (group 5) where the partial-reporting rule can be applied

## 2. Resolve cost in the estimator

- [x] 2.1 Add a cost-source type with `API`, `API_CACHED`, `TABLE`, `UNKNOWN`
- [x] 2.2 Add a `CostEstimator` entry point that resolves reported → table → unknown and returns both the value and its source
- [x] 2.3 Keep the reported value on the exact/fractional-cent path so sub-cent costs stay visible
- [x] 2.4 Leave `estimatePipelineCostCents` and `estimateScoringCostCents` on the configured rates — they run before any call and have no reported cost available
- [x] 2.5 Keep persisted integer-cent rounding and the budget gate unchanged

## 3. Cache the reported cost

- [x] 3.1 Add the reported-cost field to the `LlmCache` entity
- [x] 3.2 Persist it in `CachingChatModel` on a cache miss, alongside the existing token counts
- [x] 3.3 Attach it to the reconstructed response's `ChatResponseMetadata` in `reconstructResponse` on a cache hit
- [x] 3.4 Confirm a legacy cache row (no stored cost) replays token counts only and resolves to `TABLE`
- [x] 3.5 Confirm blank completions are still never cached

## 4. Persist and aggregate the source

- [x] 4.1 Write `V64__add_llm_cost_source.sql` adding a nullable reported-cost column to `llm_cache`, a nullable reported-cost column to `articles`, and a nullable `llm_cost_source` to `episodes`, with no backfill
- [x] 4.2 Add the `llmCostSource` field to the `Episode` entity
- [x] 4.3 Aggregate per-stage sources into the episode value: `API` when all reported, `API_CACHED` when all replayed, `TABLE` when none, `MIXED` when some, `UNKNOWN` when nothing resolved
- [x] 4.4 Write the aggregate through the same `EpisodeService` path that already maintains the aggregate cost columns, so it cannot drift
- [x] 4.5 Extend `V64` with four nullable `REAL` per-stage reported-cost columns on `episodes` (`score_`, `dedup_`, `compose_`, `recap_reported_cost_cents`), in fractional cents, no backfill
- [x] 4.6 Add the four nullable `Double?` fields to the `Episode` entity
- [x] 4.7 Add `ResolvedLlmCost.reportedCostCents`, returning the cost for `API` / `API_CACHED` / `MIXED` and null for `TABLE` / `UNKNOWN`, so the rule lives in one place instead of at four call sites
- [x] 4.8 Document in KDoc that a `MIXED` stage's persisted value includes the configured-rate gap estimate, which the episode's `llm_cost_source` already marks

## 5. Thread it through the pipeline

- [x] 5.1 Persist the per-call reported cost onto the article row in `ArticleScoreSummarizer`, alongside the existing token accumulation
- [x] 5.2 Aggregate the score stage in `LlmPipeline`: sum the reported per-article costs, estimate the non-reporting articles from their own tokens and the configured rate, and add the two
- [x] 5.3 Derive the score stage's source: `API` when every article reported, `TABLE` when none did, `MIXED` otherwise
- [x] 5.4 Keep the existing rule that score cost is never summed from per-article `llm_cost_cents`, which rounds sub-cent calls to zero
- [x] 5.5 Pass the reported cost through the dedup and compose stages in `LlmPipeline` (single call each, so no partial case)
- [x] 5.6 Pass it through `EpisodeRecapGenerator`
- [x] 5.7 Verify the two budget-gate call sites (`LlmPipeline` lines around 129 and 198) still compare rounded integer cents
- [x] 5.8 Carry the per-stage reported cents on `PipelineResult`, `DedupStageResult` and `ComposeStageResult`, from `scoreStageCost` and the dedup and compose `resolveLlmCost` sites
- [x] 5.9 Return the reported cents from `EpisodeRecapGenerator` alongside its existing `costSource`
- [x] 5.10 Write all four values through the existing `EpisodeService` paths (`createEpisodeFromPipelineResult`, `saveDedupResults`, `saveComposeResult`, the recap path) so they cannot drift from the per-stage cost columns

## 6. Surface it in the API

- [x] 6.1 Add `costSource` to the costs DTO in `PodcastMappers`
- [x] 6.2 Prefer a persisted reported cost over recomputing the stage row from tokens and rates
- [x] 6.3 Keep the existing fallbacks in order: reported → recomputed from tokens → persisted integer cents
- [x] 6.4 Confirm legacy episodes return a null `costSource` and unchanged numbers
- [x] 6.5 Prefer the persisted reported cents on all four stage rows in `buildCosts`, falling back to the existing recompute-then-persisted logic when null
- [x] 6.6 Drop `ScoreStageSummary.reportedCostCents` and its re-aggregation over `articles.llm_reported_cost_usd`, superseded by the persisted `episodes.score_reported_cost_cents` (which also covers the `MIXED` case); keep the `calls` count and the article column itself, which `LlmPipeline` still aggregates

## 7. Tests

- [x] 7.1 `TokenUsage` extraction: reported cost present, absent, malformed, negative, and carried via metadata
- [x] 7.2 `CostEstimator` resolution: reported preferred, table fallback, unknown, and sub-cent precision preserved
- [x] 7.3 `CachingChatModel`: cost stored on miss, replayed on hit, legacy row without a stored cost, blank response not cached
- [x] 7.4 Source aggregation: all-reported, all-cached, none, mixed, nothing resolved
- [x] 7.5 Score-stage partial reporting: all 40 articles report; 38 report and 2 do not (total is the sum plus the gap estimate, source `MIXED`); none report; non-reporting articles with no configured rate contribute nothing but still force `MIXED`
- [x] 7.6 Assert a partial sum is never recorded as source `API`
- [x] 7.7 Migration test covering an existing database with episodes and cache rows
- [x] 7.8 Cost breakdown mapping: reported preferred over recomputation, and the existing sub-cent scenario still passing
- [x] 7.9 Run the full suite and confirm it is green
- [x] 7.10 Migration test: the four per-stage reported-cost columns round-trip and read null when nothing was reported
- [x] 7.11 `ResolvedLlmCost.reportedCostCents`: carried for `API` / `API_CACHED` / `MIXED`, null for `TABLE` / `UNKNOWN`
- [x] 7.12 Cost breakdown mapping: each of the four rows prefers its persisted reported cost, and a null one falls back to recomputation and then to persisted cents
- [x] 7.13 `EpisodeService`: `saveDedupResults` and `saveComposeResult` persist the per-stage reported cents, and leave them null when nothing was reported

## 8. Verification

- [ ] 8.1 Generate one real episode and confirm the recorded cost matches the OpenRouter dashboard for the same calls — not done: requires a live API call and a running application
- [ ] 8.2 Regenerate the same episode and confirm the cache replay reports the same cost with source `API_CACHED` — not done: requires a live API call and a running application
- [ ] 8.3 Confirm a direct-`openai` stage still records `TABLE` — not done: requires a live API call and a running application
- [ ] 8.4 Restart the application so the running instance serves the new build — not done: requires a live API call and a running application
- [x] 8.5 Update `CLAUDE.md` to note that OpenRouter costs are provider-reported, so manual rate verification now only matters for the fallback and for non-OpenRouter providers
