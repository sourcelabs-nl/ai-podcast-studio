## Context

Articles arrive at the compose stage with whatever `body` or `summary` was captured during polling. There is no enrichment step. The compose model has no access to the web. Tavily is a search API purpose-built for LLM agents (clean snippets + URLs, free tier covers low volume, trivial JSON request). Spring AI tool-calling is the natural integration point.

This change assumes the `ChatClientFactory` compose path and the `ToolBudget` wrapper either already exist (introduced by `add-episode-history-lookback`) or are introduced fresh in this change with the same shape.

## Goals / Non-Goals

**Goals:**

- Let the compose LLM identify the most newsworthy story per episode and pull 1–3 outside snippets of context.
- Make it opt-in per podcast so most podcasts don't pay any extra cost.
- Track cost per episode so it's visible to the user.
- Cache responses so identical re-runs are free and deterministic.
- Fail-safe: Tavily downtime/errors MUST NOT block episode generation.

**Non-Goals:**

- Open-ended agent loops with multiple research backends. One tool, one backend, hard budget.
- Replacing `WebsiteFetcher`/`ContentExtractor`. Tavily is for discovery + clean snippets, not for fetching arbitrary article bodies.
- Multi-tenant Tavily keys with quotas. Each user supplies their own key (or the env fallback).

## Decisions

### D1. Tavily as the web-search backend

Tavily returns title + URL + clean snippet in one call. Alternatives considered:

- Brave Search API — general SERP, paid from the first call, more parsing.
- Perplexity — already-synthesised brief, but expensive and overlaps with the compose LLM's strengths.
- URL-fetch-only — reuses `WebsiteFetcher` but can't discover new sources, defeats the deep-dive intent.

### D2. Opt-in per podcast via `deepDiveEnabled`

Most podcasts shouldn't pay any Tavily cost. The flag defaults to `false`. The compose path checks it and registers the tool only when `true`. When `false`, the prompt never mentions `webSearch`.

### D3. Per-episode budget of 3 `webSearch` calls

Three calls is enough to research one topic thoroughly (e.g. company background, recent context, dissenting opinion) without runaway costs. The `ToolBudget` wrapper (same shape as the history change) counts invocations per compose run and returns an empty result with `budgetExhausted=true` on overflow — no exceptions.

### D4. Fail-safe error handling

Tavily client has a 10-second timeout. HTTP errors, timeouts, and missing keys all surface as an empty result list with a logged warning. The compose stage continues; the LLM sees no results and writes the script without outside context. Episode generation never fails because of a research tool error.

### D5. Tavily key via `UserProviderConfigService` + env fallback

We follow the existing per-user encrypted-API-key pattern. New `ApiKeyCategory.RESEARCH`, new `tavily` provider name. `UserProviderConfigService.resolveConfig(userId, RESEARCH, "tavily")` returns the user's key first, then falls back to the `TAVILY_API_KEY` environment variable. Without either, the tool returns "no results" and logs a warning once per episode.

### D6. Caching keyed on `(query, max_results)`

New `research_cache(query_hash TEXT PRIMARY KEY, query TEXT, max_results INTEGER, response_json TEXT, cached_at TIMESTAMP)`. Identical queries within the cache window are served without an outbound HTTP call. Cache is per-user-not-required because the *results* are public and identical regardless of who searched. Reproducibility for re-runs is the primary value.

No TTL in v1; results don't expire. If staleness becomes a problem we add a `cached_at < ?` filter.

### D7. Cost tracking on the episode

`episodes.research_calls` (INTEGER, default 0) and `research_cost_cents` (INTEGER, nullable). Compute at end of compose: `research_calls = budget.usedCount("webSearch")`; `research_cost_cents = research_calls * configured_per_call_cents`. Cached calls count toward `research_calls` (they still used a "budget slot") but the cost calculation may or may not bill cached hits — for v1 we bill all calls equally to keep the math simple.

### D8. Cost gate buffer

When `deepDiveEnabled=true`, the cost gate adds `app.research.cost-buffer-cents` (default 5¢) to the pre-call estimate. This prevents podcasts from being blocked by a tight `maxLlmCostCents` solely because of unestimated research overhead. The buffer is fixed (not proportional) to keep the estimator simple.

### D9. Migration is additive

One migration adds:

- `deep_dive_enabled INTEGER NOT NULL DEFAULT 0` to `podcasts`.
- `research_calls INTEGER NOT NULL DEFAULT 0`, `research_cost_cents INTEGER` (nullable) to `episodes`.
- `research_cache(...)` table.

All additive; existing data is unaffected.

### D10. Reuse history-lookback infra when present

If `add-episode-history-lookback` has landed, this change reuses its `ChatClientFactory.createForCompose(...)` entry point and `ToolBudget` wrapper — simply adds a conditional `webSearch` tool registration alongside `searchPastEpisodes`. If it has NOT landed, this change introduces the same patterns scoped to its own tool. Either way the surface is the same.

## Risks / Trade-offs

- **[Risk] Tavily rate limits or downtime block episode generation.** → Mitigation: 10s timeout, swallow errors as empty results, never propagate.
- **[Risk] LLM ignores the tool.** → Mitigation: explicit prompt nudge ("pick the most newsworthy story, call `webSearch` 1–2 times"); live-run verification step asserts the tool is invoked at least once on a deep-dive podcast.
- **[Risk] Costs balloon if the LLM gets stuck calling the tool.** → Mitigation: hard budget of 3 calls; budget exhaustion returns empty results, doesn't throw, so the LLM can't trigger more by retrying.
- **[Risk] Cached results go stale.** → Acceptable in v1; reproducibility outweighs freshness for the regeneration use case. Add TTL later if needed.
- **[Trade-off] Tavily is a single backend.** → Acceptable; can be abstracted into a `ResearchClient` interface later.
- **[Risk] Without history-lookback change landed, this change introduces tool-calling fresh, including `CachingChatModel` audit.** → Mitigation: tasks include the audit and the same regression test as the history change.

## Migration Plan

1. Land the migration (additive only; defaults preserve existing behavior).
2. Deploy the backend with `deepDiveEnabled=false` defaulted everywhere. No podcast changes behavior until edited.
3. Frontend ships the toggle and the Tavily API key entry. Users opt in per podcast.
4. Without a Tavily key (user or env), `deepDiveEnabled=true` becomes a no-op with a logged warning.

Rollback: revert code; the new columns and `research_cache` table can stay (defaulted/nullable) or be dropped in a follow-up.

## Open Questions

- Should `researchCostCents` include cached-hit cost (zero) or treat all calls equally? v1: treat all equally; refine if cache hit rate becomes high enough to matter.
- Should we expose per-episode tool-call traces in the dashboard? Not in v1; `app.log` is enough.
- Should `webSearch` accept structured filters (date, domain)? Not in v1; query string only. Tavily supports filters server-side if needed later.
