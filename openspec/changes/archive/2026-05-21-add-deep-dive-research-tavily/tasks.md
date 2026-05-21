## 1. Database migration

- [x] 1.1 Write `V<next>__add_deep_dive_and_research_tracking.sql` adding `deep_dive_enabled` to `podcasts`, `research_calls` + `research_cost_cents` to `episodes`, and creating the `research_cache` table
- [x] 1.2 Verify the migration applies cleanly against a copy of `./data/ai-summary-podcast.db`

## 2. Domain model + config

- [x] 2.1 Add `deepDiveEnabled: Boolean` to `Podcast` entity, DTO, repository row mapper, and request/response mappers (default `false`)
- [x] 2.2 Add `researchCalls: Int` and `researchCostCents: Int?` to `Episode` entity, DTO, and response mapper
- [x] 2.3 Add `RESEARCH` value to `ApiKeyCategory`; wire `tavily` as a valid provider for that category
- [x] 2.4 Add `app.research.tavily.cost-per-call-cents` and `app.research.cost-buffer-cents` to `AppProperties` and `application.yaml`
- [x] 2.5 Surface the `research` section in `GET /config/defaults`

## 3. Tavily client + cache

- [x] 3.1 Implement `TavilyClient` (`RestClient`-based) with 10s timeout, error swallowing, and request/response DTOs
- [x] 3.2 Implement `ResearchCacheRepository` keyed on `(query_hash, max_results)` over the new `research_cache` table
- [x] 3.3 Wrap `TavilyClient` with caching so identical queries reuse cached responses
- [x] 3.4 Unit-test client + cache with MockK + MockWebServer (request shape, response parsing, timeout handling, cache hit path)

## 4. Tool wiring

- [x] 4.1 Implement `ResearchTool` as a Spring AI tool callback exposing `webSearch(query)` returning up to 5 results
- [x] 4.2 Reuse the existing `ToolBudget` wrapper (from `add-episode-history-lookback`) OR introduce a local equivalent if that change has not landed; cap `webSearch` at 3 calls per episode
- [x] 4.3 Extend `ChatClientFactory.createForCompose` (or introduce it if not yet present) to conditionally register `webSearch` when `podcast.deepDiveEnabled=true`
- [x] 4.4 Unit-test the wiring: enabled podcast registers the tool, disabled podcast does not, filter/score stages never get tools

## 5. API key flow

- [x] 5.1 Implement `PUT /users/{userId}/api-keys/research` and `DELETE /users/{userId}/api-keys/research` reusing the existing encrypted-key path
- [x] 5.2 Wire `UserProviderConfigService.resolveConfig(userId, RESEARCH, "tavily")` with `TAVILY_API_KEY` env fallback
- [x] 5.3 Integration-test key storage (encryption at rest, env fallback precedence)

## 6. Prompt nudge

- [x] 6.1 When `deepDiveEnabled=true`, add a prompt block to all three composers instructing the LLM to pick the most newsworthy story and call `webSearch` 1–2 times for outside context
- [x] 6.2 Composer unit tests assert the prompt mentions `webSearch` when enabled and omits it when disabled

## 7. Cost gate + tracking

- [x] 7.1 Extend the cost gate estimator to add `app.research.cost-buffer-cents` (default 5¢) to the estimate when `deepDiveEnabled=true`
- [x] 7.2 Persist `research_calls` and `research_cost_cents` on the episode at end of compose
- [x] 7.3 Surface `researchCalls` and `researchCostCents` in the episode REST response
- [x] 7.4 Update episode-detail frontend to display research counts/cost when present

## 8. Frontend

- [x] 8.1 Add `deepDiveEnabled` toggle to `frontend/src/app/podcasts/[podcastId]/settings/page.tsx` with helper text noting the Tavily key requirement
- [x] 8.2 Add a Tavily entry to the API keys page (Settings > API Keys)
- [x] 8.3 Manual smoke test through the running app: enable deep-dive, add Tavily key, trigger generation, confirm episode detail shows `researchCalls > 0`

## 9. Verification

- [x] 9.1 Run `mvn test` and ensure all tests pass
- [x] 9.2 Restart the app via `./stop.sh && ./start.sh`
- [x] 9.3 With `deepDiveEnabled=true` and a real Tavily key, trigger generation; confirm logs show `webSearch` invocations and episode persists `research_calls > 0` and a non-null `research_cost_cents`
- [x] 9.4 Confirm a missing Tavily key produces a single warning per episode and does not break generation
- [x] 9.5 Run `openspec validate add-deep-dive-research-tavily --strict`
- [x] 9.6 Update `README.md` to document the deep-dive setting and the Tavily integration

## 10. Caching audit (only if add-episode-history-lookback has not landed)

- [x] 10.1 Audit complete: `CachingChatModel` already hashes only USER/SYSTEM messages, gates cache lookup on `isInitialCall`, and only stores responses without pending tool calls. Existing tests (`tool-loop intermediate call ... is not cached`, `cache key derives from user message only, ignoring tool-loop messages`) cover the regression scenarios.
- [x] 10.2 No adjustment needed — caching already operates as required.
