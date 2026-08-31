## 1. Scale the dedup output budget with the candidate count

- [x] 1.1 Replace `DEDUP_MAX_OUTPUT_TOKENS = 8000` with `DEDUP_TOKENS_PER_CANDIDATE = 90`, `DEDUP_MIN_OUTPUT_TOKENS = 8000` and `DEDUP_MAX_OUTPUT_TOKENS = 32000`
- [x] 1.2 Add `internal fun dedupOutputTokenBudget(candidateCount: Int)` returning the clamped budget
- [x] 1.3 Use it for `maxTokens(...)` in the dedup request
- [x] 1.4 Replace the "well above any real output" comment with the observed cluster-to-candidate ratios that disprove it

## 2. Salvage a truncated dedup response

- [x] 2.1 Switch the call from `responseEntity(converter)` to `chatResponse()` so the raw content is available for salvage
- [x] 2.2 Extract the raw text and keep `TokenUsage.fromChatResponse` working off the same `ChatResponse`
- [x] 2.3 Try the strict `BeanOutputConverter` parse first, unchanged behaviour on success
- [x] 2.4 Add `internal fun salvageClusters(raw: String)` recovering complete `DedupCluster` elements from a truncated `clusters` array with a streaming parse, tolerating a ```json fence
- [x] 2.5 Accept a salvage only when it selects at least `appProperties.compose.maxArticles` articles; otherwise rethrow so the existing retry applies
- [x] 2.6 Inject `AppProperties` into `TopicDedupFilter` for that threshold
- [x] 2.7 Log every accepted salvage at WARN with recovered cluster count and selected article count
- [x] 2.8 Document why partial dedup output is safe to act on, and why the compose cap is the right threshold

## 3. Reject an unregenerable episode before creating one

- [x] 3.1 Add `EpisodeNotRegenerableException` in a new `PodcastExceptions.kt`, extending `IllegalStateException` per the publishing convention
- [x] 3.2 Move the linked-article check in `PodcastService.regenerateEpisodeAsync` ahead of `createGeneratingEpisode`, throwing it
- [x] 3.3 Pass the already-loaded articles into `runRegeneration` so they are not read twice
- [x] 3.4 Add `PodcastExceptionHandler` scoped to `PodcastController`, mapping only that exception to 409 (no generic handler, so other error paths are untouched)

## 4. Tests

- [x] 4.1 `TopicDedupFilterTest`: budget scales for 183 candidates
- [x] 4.2 `TopicDedupFilterTest`: budget floors for a small candidate set
- [x] 4.3 `TopicDedupFilterTest`: budget is capped at the ceiling for a huge candidate set
- [x] 4.4 `TopicDedupFilterTest`: episode 191's truncation shape yields the complete clusters before the cut
- [x] 4.5 `TopicDedupFilterTest`: a fenced and truncated response is salvaged
- [x] 4.6 `TopicDedupFilterTest`: a response truncated mid-topic-string drops the incomplete cluster only
- [x] 4.7 `TopicDedupFilterTest`: salvage of a complete response equals the strict parse
- [x] 4.8 `TopicDedupFilterTest`: unparseable junk salvages nothing
- [x] 4.9 `PodcastServiceTest`: regenerate on an episode with no linked articles throws and creates no episode
- [x] 4.10 `PodcastServiceTest`: regenerate on an episode with articles still creates the generating episode
- [x] 4.11 Run `mvn test` and confirm the whole suite passes (1235 tests, 0 failures)

## 5. Verify against the real failure

- [x] 5.1 Confirm the 183-candidate budget (16470) exceeds the ~16,000 tokens episode 191's response needed
- [x] 5.2 Restart the app and run a real generation for the podcast, confirming dedup completes (146 candidates → 144 selected across 91 clusters in 1m44s, 3,840 output tokens, no truncation)
- [x] 5.3 Confirm a regenerate against episode 191 now returns 409 and creates no episode (verified; highest episode id stayed 193)
