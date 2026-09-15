## 1. Storage

- [x] 1.1 Add `V70__add_llm_call_log.sql` creating the `llm_calls` table with the columns from design.md and an index on `started_at`; verify the app starts and Flyway reports V70 applied with no checksum error
- [x] 1.2 Add the `LlmCall` entity and `LlmCallRepository` following the project's Spring Data JDBC conventions; verify a repository test inserts a row and reads it back with every column round-tripping (including the nullable `reported_cost_usd` and `error_type`)

## 2. Recording

- [x] 2.1 Add an `LlmCallLogService` that writes one record in its own transaction (`REQUIRES_NEW`) and swallows any exception with a log line; verify a unit test where the repository throws still returns normally and logs
- [x] 2.2 Pass `ResolvedModel` and the new service into `CachingChatModel` from `ChatClientFactory.buildCachingModel`; verify the project compiles and existing `CachingChatModel` tests are updated to the new constructor signature
- [x] 2.3 Record a successful call: time `delegate.call(prompt)` only, and write a row with stage, provider, model, duration, tokens, cost, `cacheHit = false`, `outcome = ok`; verify a test asserts one row with a non-zero duration and that the cache lookup/write time is excluded
- [x] 2.4 Record a cache hit with `cacheHit = true` and zero duration, without invoking the delegate; verify a test asserts the row is written and the delegate is never called
- [x] 2.5 Record a failed call with `outcome = error` and the exception class in `error_type`, then rethrow unchanged; verify a test asserts the original exception reaches the caller and the row exists
- [x] 2.6 Verify the tool loop produces one row per round-trip: test that a response with pending tool calls followed by a final response writes two rows, neither spanning both

## 3. Reading

- [x] 3.1 Add repository queries computing p50/p90/p95/p99 and the sample count per stage over a window, filtering `cache_hit = 0 AND outcome = 'ok'`; verify a test with a known set of durations returns the expected percentile values
- [x] 3.2 Add the service and controller exposing the percentiles per stage for a requested window, with a response DTO in its own file; verify a MockMvc test returns every stage including one with a zero sample count
- [x] 3.3 Verify the controller holds no business logic and does not touch the repository directly (architecture guideline check)

## 4. Retention

- [x] 4.1 Add `app.llm.call-log.retention` (a `Duration`) to `AppProperties` and `application.yaml` with a documented default; verify the app starts and the property binds
- [x] 4.2 Add a daily scheduled cleanup deleting records older than the retention window, scheduled off the generation window; verify a test asserts only rows older than the cutoff are deleted

## 5. Verification

- [x] 5.1 Run `mvn test` and confirm the full suite passes
- [ ] 5.2 Restart the app (`./stop.sh` then `./start.sh`), generate or score against a real podcast, and confirm via the API that rows were recorded with plausible per-request durations and that a compose run produced more than one row
- [x] 5.3 Run `/code-review --all` and fix violations, repeating until the review is clean
