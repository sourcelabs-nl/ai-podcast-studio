## 1. Determine what actually suppresses reasoning

- [x] 1.1 Probe the live OpenRouter API on the model production resolves (`~deepseek/deepseek-v4-flash-latest`, the podcast's override) with no reasoning block, `effort: "low"`, `effort: "none"` and `enabled: false`, each alongside the production `provider` block; verify by comparing `usage.completion_tokens_details.reasoning_tokens` across the four (measured 47 / 22 / 0 / 0)
- [x] 1.2 Confirm `extraBody` is genuinely serialized into the request rather than dropped by Spring AI, since a reasoning fix is pointless otherwise; verify by tracing `OpenAiChatOptions.getExtraBody()` through `OpenAiChatModel.createRequest`, `JsonValue.from`, `Body.additionalProperties` and `combineWith` in the pinned sources
- [x] 1.3 Probe `effort: "none"` against the compose model (`z-ai/glm-5.3`) before changing the shared helper; verify whether a mandatory-reasoning endpoint accepts it (it does not: HTTP 400 "Reasoning is mandatory for this endpoint and cannot be disabled")

## 2. Request shaping

- [x] 2.1 In `OpenRouterRouting.extraBodyFor`, build the `reasoning` block for any non-null effort rather than skipping it when the effort is `NO_REASONING`; verify by the routing test asserting `effort` `none` and `exclude` travel alongside the `provider` floor
- [x] 2.2 Rewrite the KDoc on `extraBodyFor` and `NO_REASONING` to state that omitting the block selects the model's default rather than none, with the measured token counts and the `mandatory: true` caveat; verify by reading the file back against the probe results
- [x] 2.3 Replace the `TopicDedupFilter` call-site comment claiming reasoning is disabled by the model; verify the comment matches the measured behaviour

## 3. Diagnostics

- [x] 3.1 Add an `isAbnormalFinishReason` predicate and a WARN carrying the finish reason and the request's output-token budget, emitted only when the reason is neither a normal stop nor absent; verify by unit tests covering `LENGTH`, `STOP` (both cases), null and blank

## 4. Corrections to stale guidance

- [x] 4.1 Correct the `application.yaml` comment on the `dedup` model default, which claimed `TopicDedupFilter` disables reasoning via `reasoningEffort("none")`; verify the comment names the explicit `effort: "none"` and the measured token counts
- [x] 4.2 Correct the `app.compose.reasoning-effort` comment, which recommended turning the effort down to `none` without noting that some endpoints reject it; verify the comment carries the glm-5.3 400

## 5. Verification

- [x] 5.1 Run `mvn test` and confirm the whole suite passes (1321 tests, no failures)
- [x] 5.2 Restart the app and retry episode 200 through the REST API; verify from `app.log` that dedup logs `[Dedup] Filter complete in ...` rather than `Error reading response` (completed in 1m19.5s, 133 candidates → 132 selected across 82 clusters, no retries)
- [x] 5.3 Read episode 200's `costs.dedup` from the episodes API and compare against the 2,255-3,840 output-token baseline of episodes 194-197; verify it falls in range (1 call, 20,890 in / 4,124 out, 0.20 cents) and that the episode reached `GENERATED`
- [x] 5.4 Run the code reviewer and fix any violations, repeating until clean per the project's code review loop; verify the second pass reports no violations (pass 1: 0 violations, 2 warnings — the finish-reason extractor was refactored to read `ChatResponse` so the metadata read is covered; pass 2: 0 violations, 0 warnings)
