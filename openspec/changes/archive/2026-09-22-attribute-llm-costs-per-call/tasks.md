## 1. Record the cost with the call

- [x] 1.1 Add nullable `resolved_cost_usd` and `cost_source` columns to `llm_calls` in a new Flyway
      migration, and verify a migration test asserts existing rows carry null in both and no existing
      value changed
- [x] 1.2 Move the per-call cost resolution (reported value, else configured rates, else none) into
      the telemetry recording path so it is written in the same insert as the request, and verify a
      test covers all three sources including the undetermined one
- [x] 1.3 Keep `LlmPipeline` and `EpisodeService` writing the existing episode columns unchanged, and
      verify `mvn test` still passes with both writers in place

## 2. Prove the failed run survives

- [x] 2.1 Add a test that a request recorded for an episode keeps its cost after the stage that
      issued it fails and rolls back, reproducing the episode 226 case
- [x] 2.2 Verify against the live database that both of episode 226's dedup calls and both of its
      dedup-gate calls carry a resolved cost after the migration

## 3. Close the score attribution gap

- [x] 3.1 Backfill `llm_calls.article_id` in the migration, linking a request to an article only when
      exactly one candidate matches on token counts within that article's scoring window, and verify a
      test covers the ambiguous case staying unattributed
- [x] 3.2 Report the coverage the backfill achieved (requests attributed out of requests eligible) and
      verify it against episode 226's 178 candidates before relying on it
- [x] 3.3 Add the per-episode score completeness check (every recorded candidate has an attributed
      scoring request) and verify a test covers a complete and an incomplete episode

## 4. Project the episode cost

- [x] 4.1 Add a per-stage cost and token projection over `BELONGS_TO_EPISODE` to the call repository,
      and verify a test asserts it sums every request of a stage including repeated attempts
- [x] 4.2 Apply the per-stage fallback gate: project an episode-tagged stage above the attribution
      boundary, project scoring only when complete, read the persisted column otherwise, and verify
      tests cover each branch
- [x] 4.3 Derive the reported cost source from the sources of exactly the requests counted, and verify
      a test covers a stage mixing reported and estimated calls
- [x] 4.4 Serve the breakdown from the projection at full precision, rounding to cents once, and
      verify a test covers a stage of many sub-cent calls not rounding to zero

## 5. Make the two views agree

- [x] 5.1 Verify by test that a fully attributed episode reports the same request count per stage in
      the latency view and the cost breakdown
- [x] 5.2 Verify against the live application that episode 226 shows two dedup and two dedup-gate
      calls in both views, and that its score stage is unchanged

## 6. Close out

- [x] 6.1 Measure the episode detail read against the projection and confirm it did not regress; cache
      per episode only if it did (9-15 ms over five reads of episode 226; no cache added)
- [x] 6.2 Run `mvn test` and `/code-review --all`, fix violations, and repeat until the review is clean
- [x] 6.3 Record in `knowledge/` what the episode 226 case established about where a cost must be
      written to survive a failure, and update `knowledge/log.md` and the section index
