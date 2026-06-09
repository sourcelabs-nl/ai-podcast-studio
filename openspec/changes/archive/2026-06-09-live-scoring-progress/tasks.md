# Tasks

- [x] Add an `onProgress(completed, total)` callback to `ArticleScoreSummarizer.scoreSummarize`, incremented in a `finally` per article and throttled to ~50 callbacks per run (step = max(1, total/50), always emitting the final count).
- [x] Wire the callback in `LlmPipeline.aggregateScoreAndFilter` and `LlmPipeline.preview` to re-emit the `scoring` stage event with `scoredCount` and `articleCount`.
- [x] Update `LlmPipelineTest` mock stubs/verifications for the new parameter.
- [x] Guard the `onProgress` lambdas in `PodcastService` (generation + retry paths) to persist `pipelineStage` only on an actual stage transition, while still emitting an event per progress tick — avoids ~50 redundant episode-row writes/version bumps during the concurrent scoring phase.
- [x] Frontend: store scoring progress per episode id from incremental `scoring` events; render `Scoring X / N` and a progress bar on the GENERATING row; skip list re-fetch on progress ticks.
- [x] Run `mvn test` (full suite green) and frontend `tsc --noEmit`.
