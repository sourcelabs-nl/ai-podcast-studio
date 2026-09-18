## 1. Storage

- [ ] 1.1 Add `V72__add_llm_call_episode_id.sql` adding a nullable `episode_id INTEGER` to `llm_calls` and an index on `(episode_id, stage)`; verify the app starts and Flyway reports V72 applied with no checksum error
- [ ] 1.2 Add `episodeId: Long?` to the `LlmCall` entity; verify a repository test round-trips a row both with an episode and with `NULL`

## 2. Recording

- [ ] 2.1 Add `episodeId: Long?` to `LlmCallRecord` and carry it through `LlmCallLogService.record` to the entity; verify a unit test asserts the id reaches the persisted row and that a `null` id still writes a complete row
- [ ] 2.2 Add an `episodeId: Long?` constructor parameter to `CachingChatModel` and write it on every recorded row (success, cache hit and failure); verify tests assert all three row kinds carry the id, and update existing tests to the new signature
- [ ] 2.3 Add `episodeId: Long? = null` to `ChatClientFactory.createForModel`, `createForCompose` and `buildCachingModel`, passing it to the model; verify the project compiles and that a caller omitting it records `NULL`

## 3. Attribution at the call sites

- [ ] 3.1 Add `episodeId` to `ComposeContext` and pass it from `BriefingComposer`, `DialogueComposer` and `InterviewComposer` into `createForCompose`; verify a test asserts a compose request issued inside `withContext(Dispatchers.IO)` still records the episode
- [ ] 3.2 Add an `episodeId` parameter to `ScriptJudge.judge` and supply `episode.id` from `EpisodeScoringService`; verify a test asserts an eval request records the episode being scored
- [ ] 3.3 Pass the episode id through `LlmPipeline` for the filter and dedup stages from `PodcastService.runGenerationPipeline`, which already holds `generatingEpisode.id`; verify a test asserts scoring and dedup rows carry the episode
- [ ] 3.4 Confirm the non-episode paths (`preview`, `scoreReadySources`, recompose without an episode) record `NULL` rather than an unrelated id; verify a test asserts a preview run writes rows with no episode

## 4. Reading

- [ ] 4.1 Extend `latencyPercentilesSince` to take an optional episode, applying it to both the grouping query and the nearest-rank offset query and skipping the time cutoff when an episode is named; verify a test with known durations across two episodes returns each episode's own percentiles
- [ ] 4.2 Add a repository query returning one episode's individual requests (stage, model, started at, duration, outcome, cache hit), including cached and failed rows; verify a test asserts ordering and that both are included
- [ ] 4.3 Add a repository query returning the earliest `started_at` over rows carrying an episode, used to decide whether an episode predates attribution; verify a test asserts it returns null when no attributed row exists
- [ ] 4.4 Extend `LlmCallLatencyService` and `LlmCallLatencyController` so `GET /llm/calls/latency` accepts an optional episode, and add the per-episode request read alongside it, with response DTOs in their own file; verify MockMvc tests cover the windowed form, the episode form, and an episode predating attribution
- [ ] 4.5 Verify the controller holds no business logic and does not touch the repository directly (architecture guideline check)

## 5. Frontend

- [ ] 5.1 Add the new response types to `frontend/src/lib/types.ts`; verify `npx tsc --noEmit` passes
- [ ] 5.2 Give `LatencyTab` the episode it is rendered for and fetch the episode-scoped percentiles, updating the heading to state the episode scope; verify the tab renders the episode's figures on the episode detail page
- [ ] 5.3 Render the episode's individual requests beneath the percentiles, marking failed and cache-served requests distinctly; verify a request list appears for an episode with recorded requests
- [ ] 5.4 Show a distinct message for an episode that predates attribution rather than a zero count; verify an older episode (for example #224) says so instead of reporting no requests
- [ ] 5.5 Keep the tab's independent failure behaviour: a failed fetch reports itself and leaves the rest of the page working; verify by forcing the request to fail

## 6. Verification

- [ ] 6.1 Run `mvn test` and confirm the full suite passes
- [ ] 6.2 Restart the app (`./stop.sh` then `./start.sh`), generate an episode end to end, and confirm via the API that its scoring, dedup, compose and eval rows all carry that episode and that the tab shows them
- [ ] 6.3 Run `/code-review --all` and fix violations, repeating until the review is clean
- [ ] 6.4 Update `knowledge/` if the per-episode figures reveal anything about stage latency worth recording, per the Knowledge Bundle rules
