## 1. Storage

- [x] 1.1 Add `V84__add_llm_call_generation_stats.sql` adding nullable `generation_id`, `ttft_ms`, `generation_time_ms`, `native_completion_tokens`, `native_reasoning_tokens`, `finish_reason`, `provider_attempts_json` to `llm_calls`; verify the app starts and Flyway applies it
- [x] 1.2 Extend the `LlmCall` entity and `LlmCallRow`/per-episode query with the new columns; verify existing `LlmCallRepository` tests pass

## 2. Recording the generation id

- [x] 2.1 Add `generationId` to `LlmCallRecord`, read from `ChatResponseMetadata.id` in `CachingChatModel.recordCall` (null for cache hits and failures); verify with a `CachingChatModel` unit test
- [x] 2.2 Make `LlmCallLogService.record` return the saved row id (null on write failure) and add `recordGenerationStats(rowId, stats)` in its own transaction that logs and swallows failures; verify with service tests

## 3. Generation stats lookup

- [x] 3.1 Add `GenerationStatsLookup` (base URL + API key) and have `ChatClientFactory` pass it to `CachingChatModel` for OpenRouter models only; verify existing factory tests and constructor call sites compile
- [x] 3.2 Implement `OpenRouterGenerationClient` (RestClient, response data class mapping `latency`, `generation_time`, `native_tokens_completion`, `native_tokens_reasoning`, `finish_reason`, `provider_name`, `provider_responses`), 404 → not yet; verify with a MockRestServiceServer test using the probed payload
- [x] 3.3 Implement `GenerationStatsService` with a supervisor scope and bounded dispatcher: first try after 5 s, retry every 10 s, give up after 3 min with a warning, then `recordGenerationStats`; verify with a test covering delayed success and give-up (virtual time)
- [x] 3.4 Call the service from `CachingChatModel` after a successful non-cached call when a lookup and row id are present; verify with a unit test that cache hits, failures and non-OpenRouter calls schedule nothing

## 4. API exposure

- [x] 4.1 Add the stats fields (first answer token, generation time, native tokens, finish reason, attempts as a list) to the `GET /llm/calls/episodes/{id}` response; verify with a controller test

## 5. Experiment breakdown

- [x] 5.1 Add a `ComposeBreakdown` data class and derive it from a run's compose rows in `ExperimentService.metricsOf` (null when any compose row lacks stats); add it to `ExperimentRunMetrics` and averaged into `ExperimentVariantMeans`; verify with tests for the spec's with-stats and without-stats scenarios

## 6. Frontend

- [x] 6.1 Extend `lib/types.ts` and `components/latency-tab.tsx` with TTFT, generation time, tokens/s, provider and attempts (expandable when >1, placeholder when missing); verify with `npx tsc --noEmit` and a visual check via a subagent

## 7. Verification and knowledge

- [x] 7.0 Rename `ttft_ms` to `first_content_ms` (V85) and derive request phases (startup, reasoning, writing) from the live-verified field meanings; verify with `GenerationStatsPhasesTest` and the episode 276 response

- [x] 7.1 Run `mvn test` and fix any failures; restart the app with `./stop.sh && ./start.sh`
- [x] 7.2 Run one compose (regular episode or experiment) against live OpenRouter and verify its `llm_calls` row gets the stats within ~1 minute and the experiment comparison shows the breakdown
- [x] 7.3 Record the generation endpoint behaviour (delay, fields, native vs normalised tokens) in `knowledge/` via `/kb-add`
