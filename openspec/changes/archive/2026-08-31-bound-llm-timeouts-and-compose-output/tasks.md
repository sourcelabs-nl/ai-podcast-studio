## 1. Carry the stage with the resolved model

- [x] 1.1 Add `stage: PipelineStage` to `ResolvedModel`
- [x] 1.2 Populate it in `ModelResolver.resolve`
- [x] 1.3 Update every `ResolvedModel(...)` construction in tests

## 2. Per-stage request timeouts

- [x] 2.1 Add `StageTimeouts(filter=3m, dedup=5m, compose=20m)` to `LlmProperties` as `timeouts`
- [x] 2.2 Surface the values in `application.yaml` with the measured durations that justify them
- [x] 2.3 Inject `AppProperties` into `ChatClientFactory` and select the timeout by `resolvedModel.stage`
- [x] 2.4 Replace the blanket-20-minute comment with the per-stage rationale, naming the 18m11s compose run

## 3. Compose output ceiling

- [x] 3.1 Add `maxOutputTokens: Int = 96000` to `ComposeProperties`
- [x] 3.2 Surface it in `application.yaml` with the observed min/avg/max compose output
- [x] 3.3 Apply `.maxTokens(...)` in `BriefingComposer`, `DialogueComposer` and `InterviewComposer`

## 4. Tests

- [x] 4.1 `ChatClientFactoryTest`: filter stage gets the filter timeout
- [x] 4.2 `ChatClientFactoryTest`: dedup stage gets the dedup timeout
- [x] 4.3 `ChatClientFactoryTest`: compose stage gets the compose timeout
- [x] 4.4 Composer tests: the compose request carries the configured `maxTokens`
- [x] 4.5 Run `mvn test` and confirm the whole suite passes (1256 tests, 0 failures)

## 5. Verify

- [x] 5.1 Restart and confirm a real generation still composes successfully under the ceiling
