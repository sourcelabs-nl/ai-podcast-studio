## Context

Episode cost is currently tracked at three levels: per-article (Stage 1 only, in `articles.llm_*`), aggregate LLM (`episodes.llm_*`, which sums Stage 1+dedup+compose+recap), TTS (`episodes.tts_*`), and research (`episodes.research_*`). For users investigating spend per episode, the LLM aggregate is opaque — they can't tell whether a $0.42 episode was driven by expensive scoring across 100 articles or by an expensive compose model.

This design adds a per-stage rollup on the episode and surfaces it as a "Costs" tab. Granularity chosen: per-stage rollup (rejected alternatives: per-call audit log and JSON blob).

## Goals / Non-Goals

**Goals:**
- Editors can see, on each episode, how spend breaks down across scoring / dedup / compose / recap / TTS / research, with a total.
- Recap cost is computed and persisted (today it's silently dropped).
- The existing aggregate `llmCostCents` stays correct and continues to drive headers / list views — no flag day for the rest of the UI.
- Stage 1 score cost computed from token sums (not from `articles.llm_cost_cents` which rounds sub-cent calls to 0).
- Backwards-compatible: legacy episodes show a notice; new stage cost cells are 0 for them but aggregate `llmCostCents` still reads the historical value.

**Non-Goals:**
- Per-call audit log. A table with one row per LLM invocation would give the finest grain but doubles schema scope and the rejected granularity option already covers what editors actually need.
- Per-article cost detail in the Costs tab. Per-article tokens already live in the Articles tab; cross-linking is future work.
- Cached-vs-real-spend distinction. Cache hits still report tokens (and thus a notional cost); flagging cached entries is future work.
- Backfilling dedup / compose / recap costs for pre-V57 episodes — there's no source data to reconstruct them.

## Decisions

### Decision 1: Per-stage rollup, NOT a per-call audit table

User chose this granularity over a `episode_cost_calls` audit table and over a JSON blob.

**Why:** Stage 1 already has natural per-call detail in `articles`. Dedup, compose, recap, TTS are all "one call per episode" — per-call ≈ per-stage. Research has 0–3 calls and a flat per-call rate; a `research_calls` count is enough. A typed-column rollup is the right balance: 12 new columns is cheap on SQLite, easy to query, and lets the Costs tab render without joins.

**Trade-off:** can't see WHICH specific article cost what at scoring time from the Costs tab alone. The Articles tab already exposes per-article tokens, so the link can be added later if requested.

### Decision 2: Aggregate `llmCostCents` is a derived sum

The pre-existing `episodes.llm_input_tokens`, `llm_output_tokens`, `llm_cost_cents` columns are kept but become **derived sums** of the four stage triples, written exclusively by `EpisodeService`. No other code path may update them independently.

**Why:** the episode list page, header, podcast pages, and at least three other UIs read `llmCostCents` today. Rewriting them all is unnecessary churn; the aggregate stays as a fast-read "total LLM spend" cache. A single write path means the invariant can't drift.

**Documented:** in `Episode.kt` KDoc and in private helpers `sumStageInputTokens` / `sumStageOutputTokens` / `sumStageCostCents` on `EpisodeService`.

### Decision 3: Compute `score_cost_cents` from token SUM, not from per-article costs

Per-article `articles.llm_cost_cents` is computed at scoring time as `roundToInt(totalUsd * 100)` per call. For typical scoring sizes (~500 input / 80 output tokens at $0.15/$0.60 per Mtok), that's $0.000123 per article → rounds to 0¢. Sum 41 zeros = 0. Useless.

`score_cost_cents` is instead computed in `LlmPipeline.dedup()` from `articles.sumOf { llmInputTokens } * inputRate + articles.sumOf { llmOutputTokens } * outputRate`, rounded once at the end. Precision preserved.

**Side effect:** the per-article `llm_cost_cents` column stays useless for aggregation — but it's still useful as a per-call sanity check, so we leave it alone.

### Decision 4: Backfill tokens, not costs, for pre-V57 episodes

V57 backfills `score_input_tokens` / `score_output_tokens` from `SUM(articles.llm_*)` via `episode_articles`. It does NOT backfill `score_cost_cents` (Decision 3 explains why summing per-article costs is meaningless). Dedup / compose / recap have no historical source — those columns stay at 0 for pre-V57 episodes.

The frontend renders a "detailed breakdown not available for episodes generated before this feature shipped" notice when all four LLM stage cost cells are 0 but `ttsCostCents` or `researchCostCents` is non-zero. Listeners aren't misled into thinking generation was free.

### Decision 5: Score row reports article count, other LLM rows report 1 or 0

The score stage is `N` LLM calls (one per article); dedup / compose / recap are exactly one call each. The Costs tab `calls` column reflects this directly:
- score: count of `episode_articles` rows for this episode (the surviving articles)
- dedup / compose / recap: `1` if any of (input_tokens, output_tokens, cost_cents) > 0, else `0`
- tts: `—` (rate is per-character, not per-call)
- research: `researchCalls` value (0–3)

**Why "surviving articles" not "all scored"?** A run scores ALL unscored articles (some excluded by `relevanceThreshold`), but the episode only "owns" the ones that ended up linked. Filtered-out articles' tokens are persisted on the article rows; their spend isn't attributable to a specific episode. Using `episode_articles` count keeps the attribution honest and is the count the editor sees on the Articles tab.

### Decision 6: Re-resolve model names at mapping time, no new column

The Costs tab shows the model used per LLM stage. Two options:
- Persist a resolved-model column per stage on `episodes` (storage cost, denormalized).
- Re-resolve at API response time via `ModelResolver.resolve(podcast, stage)`.

Re-resolving is chosen — the podcast is already loaded for the GET response, the resolver is pure, and any future change to model resolution naturally applies to historical episodes (which is what you want for the display label).

**Caveat:** if a podcast is reconfigured to use a different model AFTER an episode was generated, the Costs tab will show the new model name on the old episode. The persisted `filterModel` / `composeModel` columns on `episodes` give the source-of-truth and the mapper reads THOSE for the model field — `ModelResolver` is not actually invoked. (The `filterModel` and `composeModel` columns are populated at pipeline time and never updated.)

So Decision 6's final form: read `filterModel` / `composeModel` columns from `Episode` directly. Score / dedup / recap all use the filter model; compose uses the compose model.

### Decision 7: No new endpoint, extend `GET /episodes/{id}`

The existing single-episode GET gains the `costs` field. List endpoints (`GET /episodes`) also include `costs` but with `score.calls = 0` (article count not threaded through the list call, to avoid N+1 queries). The Costs tab is only shown on the detail page, which uses the single-episode endpoint, so this is fine.

**Trade-off:** the list response carries a slightly bogus `score.calls = 0`. Acceptable because the list UI doesn't display it, and the score-cost figure on the list response is still accurate.

## Risks / Trade-offs

- **[Sync risk between aggregate and stages]** → mitigation: aggregate is written EXCLUSIVELY through the three private helpers in `EpisodeService`; KDoc on `Episode` documents the invariant. New tests assert `llmCostCents == score+dedup+compose+recap`.
- **[Cached LLM responses still report tokens]** → the cache stores `(promptHash, model, response, inputTokens, outputTokens)`. Cache hits report a notional cost based on tokens, not actual API spend. Acceptable; users get a consistent "what this would cost if not cached" number. A future `cached_calls` column or a per-call audit table could disambiguate; out of scope here.
- **[Score cost only counts surviving articles]** → see Decision 5. The cost of scoring filtered-out articles is small (~$0.0001 per article × N filtered, typically < $0.005) and not attributable to any specific episode. Trade-off accepted; documented.
- **[Legacy episodes show 0 for dedup/compose/recap]** → mitigation: frontend notice. Could backfill if a future run reprocesses old scripts, but that's a heavy operation for marginal value.
- **[Per-article `articles.llm_cost_cents` stays useless]** → out of scope. We don't break it (still computed the same way), we just don't sum it for `score_cost_cents`. A future migration could replace it with a fractional-cents column if per-article cost analytics become useful.

## Migration Plan

1. Flyway migration V57 (`V57__add_episode_stage_costs.sql`):
   - Add 12 NOT NULL DEFAULT 0 INTEGER columns.
   - Backfill `score_input_tokens` and `score_output_tokens` from `SUM(articles.llm_*)` joined via `episode_articles`. Do NOT backfill cost columns.
2. Deploy backend: `Episode` entity extended, `LlmPipeline` surfaces stage totals, `EpisodeRecapGenerator` computes cost, `EpisodeService` persists per-stage and maintains aggregate. Tests assert sum invariant.
3. Deploy frontend: `EpisodeCosts` type added, `Costs` tab appears on episode detail page.
4. Next generation cycle populates all stage columns for new episodes. Legacy episodes show backfilled tokens + 0 costs + legacy notice.

Rollback: drop the 12 columns. `EpisodeService` aggregate writes still work via the helper (sum-of-zeros after rollback would write 0; revert the entity + service changes alongside the schema if needed).

## Open Questions

- Should the "Costs" tab also show a regenerate-cost button (re-run from cached prompts to get a fresh price quote at current rates)? Out of scope, possibly useful for cost-aware editing.
- Should the per-call detail (which 41 articles cost what) link from the Score row to the Articles tab? Future enhancement.
- Should TTS chars-per-second pricing be added as a model column so the rate is visible in the breakdown? Pricing is in `application.yaml` today; the breakdown only shows totals, not rates.
