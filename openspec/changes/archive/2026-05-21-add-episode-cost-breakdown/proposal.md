## Why

Today every LLM call in the pipeline (Stage 1 scoring, Stage 3 dedup, Stage 4 compose, recap) lumps its tokens and cost into a single `llmCostCents` / `llmInputTokens` / `llmOutputTokens` triple on `episodes`. Editors have no insight into where the money goes. Recap doesn't even compute its cost — `EpisodeRecapGenerator` counts tokens and silently drops them. TTS and research already have their own columns, but the LLM bucket is opaque.

This change splits the LLM aggregate into four per-stage triples on `episodes`, computes recap cost properly, and surfaces the breakdown on the episode detail page in a new "Costs" tab. The aggregate triple is kept for backwards compatibility but becomes a derived sum of the four stages.

## What Changes

- Add 12 NOT NULL DEFAULT 0 INTEGER columns to `episodes`: `score_input_tokens`, `score_output_tokens`, `score_cost_cents`, `dedup_input_tokens`, `dedup_output_tokens`, `dedup_cost_cents`, `compose_input_tokens`, `compose_output_tokens`, `compose_cost_cents`, `recap_input_tokens`, `recap_output_tokens`, `recap_cost_cents`. Single Flyway migration V57.
- Backfill `score_input_tokens` / `score_output_tokens` from `SUM(articles.llm_*)` joined via `episode_articles`. Cost cents are NOT backfilled — per-article `llm_cost_cents` rounds sub-cent scoring calls to 0 so summing it is meaningless. Going forward, `score_cost_cents` is computed from the SUM of tokens using the filter-model rate.
- `EpisodeRecapGenerator` now computes `costCents` via `CostEstimator.estimateLlmCostCents` and returns it on `RecapResult`.
- `LlmPipeline` surfaces per-stage tokens/cost in `DedupStageResult`, `ComposeStageResult`, and the top-level `PipelineResult` so `EpisodeService` can persist each stage's triple separately.
- `EpisodeService.saveDedupResults`, `saveComposeResult`, and `generateAndStoreRecap` write per-stage columns and recompute the aggregate `llm_input_tokens` / `llm_output_tokens` / `llm_cost_cents` as the sum of the four stages. Single write path, never updated independently.
- `EpisodeResponse` gains a nested `costs: EpisodeCostsResponse` object with per-stage rows (score, dedup, compose, recap), TTS row, research row, and `totalCostCents`.
- New "Costs" tab on the episode detail page renders the breakdown as a shadcn `Table`. Legacy episodes (all four LLM stage cost cells 0 but TTS/research non-zero) show a "detailed breakdown not available" notice.

## Capabilities

### Modified Capabilities
- `episode-management`: episode detail responses expose a per-stage cost breakdown; the aggregate `llmCostCents` becomes a derived sum maintained by a single code path. New stage cost-cent and token columns are persisted with the episode.
- `llm-processing`: recap stage now computes and reports its cost (previously tokens-only); per-stage cost/token totals flow through `DedupStageResult`, `ComposeStageResult`, and `PipelineResult`.

## Impact

- **Database:** 12 new INTEGER NOT NULL DEFAULT 0 columns on `episodes`. Single Flyway V57 migration including backfill of `score_*_tokens`. Reversible by dropping the columns (the aggregate `llmCostCents` continues to work via the existing code path, since it's still written by the service).
- **Backend:** `Episode`, `LlmPipeline` (`DedupStageResult`, `ComposeStageResult`, `PipelineResult`, `recompose`), `EpisodeRecapGenerator`, `EpisodeService` (4 functions), `PodcastDtos`, `PodcastMappers`, `EpisodeController` (single-episode GET threads article count for the score-row `calls` field).
- **Frontend:** new `EpisodeCosts` type and child types on `Episode`; new `components/costs-tab.tsx`; new "Costs" tab trigger on episode detail page.
- **No external API impact.** No new endpoint; the existing single-episode GET response gains the nested `costs` field.
- **Cost gate:** unchanged. The breakdown reports already-incurred spend; it does not change pricing or model resolution.
