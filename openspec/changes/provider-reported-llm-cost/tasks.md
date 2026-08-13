## 1. Capture the reported cost

- [ ] 1.1 Add a nullable `reportedCostUsd: Double?` to `TokenUsage`, defaulting to null so existing construction sites compile unchanged
- [ ] 1.2 Extract the cost in `TokenUsage.fromChatResponse` from `nativeUsage._additionalProperties()["cost"]`, wrapped so any shape change or conversion failure yields null instead of throwing
- [ ] 1.3 Treat absent, non-numeric and negative values as no reported cost
- [ ] 1.4 Resolve a metadata-carried cost first, so a cache replay takes precedence over native usage extraction
- [ ] 1.5 Delete `TokenUsage.plus` and `TokenUsage.ZERO`, which are unreferenced in both `src/main` and `src/test`; aggregation belongs at the stage level (group 5) where the partial-reporting rule can be applied

## 2. Resolve cost in the estimator

- [ ] 2.1 Add a cost-source type with `API`, `API_CACHED`, `TABLE`, `UNKNOWN`
- [ ] 2.2 Add a `CostEstimator` entry point that resolves reported → table → unknown and returns both the value and its source
- [ ] 2.3 Keep the reported value on the exact/fractional-cent path so sub-cent costs stay visible
- [ ] 2.4 Leave `estimatePipelineCostCents` and `estimateScoringCostCents` on the configured rates — they run before any call and have no reported cost available
- [ ] 2.5 Keep persisted integer-cent rounding and the budget gate unchanged

## 3. Cache the reported cost

- [ ] 3.1 Add the reported-cost field to the `LlmCache` entity
- [ ] 3.2 Persist it in `CachingChatModel` on a cache miss, alongside the existing token counts
- [ ] 3.3 Attach it to the reconstructed response's `ChatResponseMetadata` in `reconstructResponse` on a cache hit
- [ ] 3.4 Confirm a legacy cache row (no stored cost) replays token counts only and resolves to `TABLE`
- [ ] 3.5 Confirm blank completions are still never cached

## 4. Persist and aggregate the source

- [ ] 4.1 Write `V64__add_llm_cost_source.sql` adding a nullable reported-cost column to `llm_cache`, a nullable reported-cost column to `articles`, and a nullable `llm_cost_source` to `episodes`, with no backfill
- [ ] 4.2 Add the `llmCostSource` field to the `Episode` entity
- [ ] 4.3 Aggregate per-stage sources into the episode value: `API` when all reported, `API_CACHED` when all replayed, `TABLE` when none, `MIXED` when some, `UNKNOWN` when nothing resolved
- [ ] 4.4 Write the aggregate through the same `EpisodeService` path that already maintains the aggregate cost columns, so it cannot drift

## 5. Thread it through the pipeline

- [ ] 5.1 Persist the per-call reported cost onto the article row in `ArticleScoreSummarizer`, alongside the existing token accumulation
- [ ] 5.2 Aggregate the score stage in `LlmPipeline`: sum the reported per-article costs, estimate the non-reporting articles from their own tokens and the configured rate, and add the two
- [ ] 5.3 Derive the score stage's source: `API` when every article reported, `TABLE` when none did, `MIXED` otherwise
- [ ] 5.4 Keep the existing rule that score cost is never summed from per-article `llm_cost_cents`, which rounds sub-cent calls to zero
- [ ] 5.5 Pass the reported cost through the dedup and compose stages in `LlmPipeline` (single call each, so no partial case)
- [ ] 5.6 Pass it through `EpisodeRecapGenerator`
- [ ] 5.7 Verify the two budget-gate call sites (`LlmPipeline` lines around 129 and 198) still compare rounded integer cents

## 6. Surface it in the API

- [ ] 6.1 Add `costSource` to the costs DTO in `PodcastMappers`
- [ ] 6.2 Prefer a persisted reported cost over recomputing the stage row from tokens and rates
- [ ] 6.3 Keep the existing fallbacks in order: reported → recomputed from tokens → persisted integer cents
- [ ] 6.4 Confirm legacy episodes return a null `costSource` and unchanged numbers

## 7. Tests

- [ ] 7.1 `TokenUsage` extraction: reported cost present, absent, malformed, negative, and carried via metadata
- [ ] 7.2 `CostEstimator` resolution: reported preferred, table fallback, unknown, and sub-cent precision preserved
- [ ] 7.3 `CachingChatModel`: cost stored on miss, replayed on hit, legacy row without a stored cost, blank response not cached
- [ ] 7.4 Source aggregation: all-reported, all-cached, none, mixed, nothing resolved
- [ ] 7.5 Score-stage partial reporting: all 40 articles report; 38 report and 2 do not (total is the sum plus the gap estimate, source `MIXED`); none report; non-reporting articles with no configured rate contribute nothing but still force `MIXED`
- [ ] 7.6 Assert a partial sum is never recorded as source `API`
- [ ] 7.7 Migration test covering an existing database with episodes and cache rows
- [ ] 7.8 Cost breakdown mapping: reported preferred over recomputation, and the existing sub-cent scenario still passing
- [ ] 7.9 Run the full suite and confirm it is green

## 8. Verification

- [ ] 8.1 Generate one real episode and confirm the recorded cost matches the OpenRouter dashboard for the same calls
- [ ] 8.2 Regenerate the same episode and confirm the cache replay reports the same cost with source `API_CACHED`
- [ ] 8.3 Confirm a direct-`openai` stage still records `TABLE`
- [ ] 8.4 Restart the application so the running instance serves the new build
- [ ] 8.5 Update `CLAUDE.md` to note that OpenRouter costs are provider-reported, so manual rate verification now only matters for the fallback and for non-OpenRouter providers
