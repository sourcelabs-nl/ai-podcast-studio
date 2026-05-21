## 1. Database migration

- [x] 1.1 Write `V57__add_episode_stage_costs.sql` adding 12 NOT NULL DEFAULT 0 INTEGER columns to `episodes` (score_*, dedup_*, compose_*, recap_*)
- [x] 1.2 Backfill `score_input_tokens` / `score_output_tokens` from `SUM(articles.llm_*)` via `episode_articles`; do NOT backfill cost-cent columns
- [x] 1.3 Verify the migration applies cleanly against a copy of `./data/ai-summary-podcast.db` and the backfill sums match the linked articles

## 2. Domain model + pipeline plumbing

- [x] 2.1 Add the 12 stage columns to the `Episode` entity with default 0 and document the aggregate-as-sum invariant in the entity KDoc
- [x] 2.2 Add `scoreInputTokens`, `scoreOutputTokens`, `scoreCostCents` to `DedupStageResult`; compute them in `LlmPipeline.dedup()` from the filtered articles and the resolved filter model
- [x] 2.3 Add the per-stage triples to `PipelineResult` (score / dedup / compose) and populate them in `LlmPipeline.run()` and `LlmPipeline.recompose()`
- [x] 2.4 Extend `RecapResult` with `costCents: Int?` and compute it in `EpisodeRecapGenerator.generate` via `CostEstimator`

## 3. EpisodeService persistence

- [x] 3.1 In `saveDedupResults`, persist `score_*` and `dedup_*` triples; reset compose/recap to 0; recompute aggregate `llm_*` from the four stages
- [x] 3.2 In `saveComposeResult`, persist `compose_*` triple; recompute aggregate (keeps any prior dedup/score values intact)
- [x] 3.3 In `generateAndStoreRecap`, persist `recap_*` triple (including cost); recompute aggregate
- [x] 3.4 Add private helpers `sumStageInputTokens` / `sumStageOutputTokens` / `sumStageCostCents` as the single write path for the aggregate columns
- [x] 3.5 In `createEpisodeFromPipelineResult`, populate all four stage triples from `PipelineResult`

## 4. API contract

- [x] 4.1 Add `EpisodeCostsResponse`, `LlmStageCostResponse`, `TtsCostResponse`, `ResearchCostResponse` to `PodcastDtos`
- [x] 4.2 Nest `costs: EpisodeCostsResponse` on `EpisodeResponse`
- [x] 4.3 Extend `PodcastMappers.toResponse(Episode)` to build the costs object; model names come from `Episode.filterModel` / `Episode.composeModel` (NOT re-resolved); `score.calls` accepted as a parameter (default 0 for list endpoints)
- [x] 4.4 Add `EpisodeService.countArticles(episodeId)` and call it from `EpisodeController.get` to thread the real article count into the score row

## 5. Frontend

- [x] 5.1 Add `EpisodeCosts`, `LlmStageCost`, `TtsCost`, `ResearchCost` types to `frontend/src/lib/types.ts`; attach `costs?: EpisodeCosts` to `Episode`
- [x] 5.2 Create `frontend/src/components/costs-tab.tsx` rendering a shadcn `Table` with six rows + total footer; format cents → `$X.XXXX`; render `—` for zero values
- [x] 5.3 Render legacy notice when all four LLM stage cost cells are 0 but TTS or research cost is non-zero
- [x] 5.4 Add `"costs"` to the `TABS` constant in the episode detail page and a `TabsTrigger value="costs"` between Publications and the end; wire `TabsContent` to `<CostsTab costs={episode.costs} />`

## 6. Tests

- [x] 6.1 Repository round-trip test for the 12 new columns (`ArticleRepositoryTest`)
- [x] 6.2 `EpisodeRecapGeneratorTest` — assert `costCents` populated when model has pricing; null when pricing missing
- [x] 6.3 `EpisodeServiceTest` — `saveDedupResults` persists score + dedup triples and aggregate sums; `saveComposeResult` persists compose triple and aggregate sums prior dedup + new compose
- [x] 6.4 `EpisodeCostsMapperTest` — `EpisodeResponse.costs` shape: totals, score article count, stage call indicators, TTS row, research row, null-collapses

## 7. Verification

- [x] 7.1 Run `mvn test` and ensure all tests pass (831 expected after additions)
- [x] 7.2 Restart the app via `./stop.sh && ./start.sh`; confirm Flyway applies V57 cleanly and backfilled `score_input_tokens` matches `SUM(articles.llm_input_tokens)` for a historical episode
- [x] 7.3 Hit `GET /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}` for a legacy episode; confirm `costs` block present, score row has backfilled tokens, dedup/compose/recap cost cells are 0
- [ ] 7.4 Live run: trigger generation on a real podcast; open the new Costs tab on the resulting episode; verify all six rows are populated with non-zero values and the Total equals the sum
- [x] 7.5 Run `openspec validate add-episode-cost-breakdown --strict`
- [ ] 7.6 Update `README.md` to mention the new Costs tab (optional, light touch)
