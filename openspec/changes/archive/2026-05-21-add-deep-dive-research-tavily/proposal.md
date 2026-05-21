## Why

Every article currently gets a small segment regardless of newsworthiness, and the compose model only has the article body/summary collected at polling time. There is no way to go deep on a single great story with outside context. This change adds an opt-in research agent: the compose LLM can identify the most newsworthy story and pull 1–3 outside snippets from the web via a Tavily-backed Spring AI tool.

## What Changes

- Add a `webSearch(query)` Spring AI tool backed by a new `TavilyClient`. Opt-in per podcast via a new `deepDiveEnabled` flag (default `false`).
- Add a `RESEARCH` API-key category and a `tavily` provider entry. Tavily key flows through the existing per-user encrypted `UserProviderConfigService` with `TAVILY_API_KEY` env fallback.
- Cap `webSearch` at 3 calls per episode via a `ToolBudget`-style wrapper. Tool errors and timeouts (10s) MUST NOT fail episode generation — return an empty result.
- Cache Tavily responses keyed on `(query, max_results)` in a new `research_cache` table for reproducibility.
- Track research costs per episode: new columns `research_calls` and `research_cost_cents` on `episodes`. Surface as `researchCalls` and `researchCostCents` in the episode REST response.
- Extend the LLM cost gate estimator with a configurable fixed buffer (`app.research.cost-buffer-cents`, default 5¢) when `deepDiveEnabled=true`.
- Add a prompt block (only when `deepDiveEnabled`) instructing the compose model to pick the most newsworthy story and call `webSearch` 1–2 times for context.
- Expose `deepDiveEnabled` in the podcast settings UI and add a Tavily key entry in the API keys section.

This change is independent of script-variety and episode-history-lookback; if the history-lookback change has already landed, this reuses its `ChatClientFactory` compose path and `ToolBudget` infra. If not, this change introduces the same patterns scoped to its own tool.

## Capabilities

### New Capabilities
- `deep-dive-research`: Optional Spring AI tool (`webSearch`) backed by Tavily that lets the compose model pull outside context for the most newsworthy story. Opt-in per podcast, budgeted, cached, cost-tracked.

### Modified Capabilities
- `llm-processing`: Compose stage conditionally registers the `webSearch` tool when `podcast.deepDiveEnabled=true`.
- `cost-tracking`: Episode response includes `researchCalls` and `researchCostCents`.
- `llm-cost-gate`: Estimator adds a fixed buffer when `deepDiveEnabled` is set on the podcast.
- `podcast-customization`: New `deepDiveEnabled` boolean field.
- `frontend-podcast-settings`: UI toggle for `deepDiveEnabled` and a Tavily API key entry in the API keys section.
- `model-registry`: Add the `tavily` research provider entry exposing per-call cost.
- `user-api-keys`: Add the `RESEARCH` API-key category and the `tavily` provider.
- `database-migrations`: New migration adding `deep_dive_enabled` on `podcasts`, `research_calls` + `research_cost_cents` on `episodes`, and the `research_cache` table.

## Impact

- **Code** — `src/main/kotlin/com/aisummarypodcast/llm/ChatClientFactory.kt` (compose path registers `webSearch` when enabled); new `research/TavilyClient.kt`, `research/ResearchTool.kt`, `research/ResearchCacheRepository.kt`; updates to `store/Podcast.kt`, `store/Episode.kt`, `store/ApiKeyCategory.kt`, episode-mapping DTOs.
- **Database** — one new Flyway migration adding `deep_dive_enabled` to `podcasts`, `research_calls`/`research_cost_cents` to `episodes`, and creating `research_cache`.
- **API** — `GET/PUT /users/{userId}/podcasts/{podcastId}` gains `deepDiveEnabled`. Episode response gains `researchCalls` and `researchCostCents`. New `PUT/DELETE /users/{userId}/api-keys/research` endpoints.
- **Frontend** — `frontend/src/app/podcasts/[podcastId]/settings/page.tsx` gains the deep-dive toggle; API keys page gains the Tavily entry.
- **Config** — `application.yaml` adds `app.research.tavily.cost-per-call-cents`, `app.research.cost-buffer-cents`. `TAVILY_API_KEY` is a new optional env fallback.
- **Dependencies** — none (Spring AI already on the classpath; `RestClient` is used as elsewhere).
- **Cost** — Tavily free tier covers expected volume; per-episode budget caps exposure.
