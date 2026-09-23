Depends on `unify-episode-epilogue` being merged first: the epilogue built there is called from `PipelineRunner` for `Deliver`/`Review`/`Sandbox` outcomes.

## Phase A: RunSpec + PipelineRunner, migrate the routes

Live checks: only the preview route is exercised against the running app, since it persists no episode; every other route is pinned by MockK tests (`PipelineRunnerTest`, `PodcastServiceTest`), and the next scheduled run is the live check for the generate path.

- [x] A.1 Define `RunPurpose`, `RunInput` (`Window`/`ArticleSet`/`Focus`), `ResumePoint` reuse, `RunOutcome` (`Deliver`/`Review`/`Transient`), `RunSpec` and `RunResult` types (`podcast/RunSpecTypes.kt`), and verify with unit tests constructing one of each `RunInput`/`RunOutcome` variant (`RunOverrides` and `Sandbox` arrive in B.6 with the code that reads them)
- [x] A.2 Implement `PipelineRunner` (`podcast/PipelineRunner.kt`): stage sequencing per input and resume point, progress reporting, per-purpose failure handling, and the outcome (including the shared epilogue)
- [x] A.3 Migrate `previewBriefing`/`LlmPipeline.preview` onto it as `purpose = PREVIEW` with outcome `Transient`, and verify: MockK unit test asserting no episode is touched and a failure propagates, `mvn clean test` passes, app restarted, live check via the preview SSE endpoint confirming a result and no new episode
- [x] A.4 Migrate `rerunEpisodeAsync` (`purpose = RERUN`, outcome `Deliver(updateLastGenerated = false)`), and verify with MockK unit tests that a re-run does not bump `lastGeneratedAt` at finalize (fixing a pre-existing bug where re-running a past window moved the schedule) while a scheduled generation still does, and `mvn clean test` passes
- [x] A.5 Migrate `regenerateEpisodeAsync` (`purpose = REGENERATE`, `ArticleSet` input), and verify with MockK unit tests: composition-only, stage events without persisted stages, `createEpisodeFromPipelineResult` with the source `generatedAt` and no `lastGeneratedAt` update, cache bypass and follow-up annotations passed through; `mvn clean test` passes
- [x] A.6 Migrate `recomposeFocusEpisodeAsync` (`purpose = RECOMPOSE`, outcome `Review`), and verify with MockK unit tests for the rewrite and for a failure leaving the episode in review; `mvn clean test` passes
- [x] A.7 Migrate focus generation (`purpose = FOCUS`), and verify with a MockK unit test for selection, focus compose context and finalize; `mvn clean test` passes
- [x] A.8 Migrate `retryEpisode` (`purpose = RETRY`), and verify with MockK unit tests for all three resume points (`FULL_PIPELINE`, `COMPOSE`, `POST_COMPOSE`), for a focus retry from compose, and for a full retry with nothing eligible failing the episode; `mvn clean test` passes
- [x] A.9 Migrate `generateBriefing` (scheduled, `purpose = SCHEDULED`) and `generateBriefingAsync` (manual, `purpose = MANUAL`), and verify with MockK unit tests for the full sequence and its stage events, an empty selection deleting the placeholder, and a failure failing the episode; `mvn clean test` passes
- [x] A.10 Delete the now-unused `PodcastService` private methods (`runGenerationPipeline`, `runFocusGenerationPipeline`, `composeAndFinalizeFocusEpisode`, `runFeedbackRecompose`, `doRetry`, `runRegeneration`, `progressReporter`) and confirm `mvn clean test` still passes

## Phase B: RunConfig, overrides, snapshot persistence, Sandbox outcome, purpose filtering

- [x] B.1 Add migration `V82__add_episode_purpose_and_run_config.sql` adding `episodes.purpose` (backfilled: `LEGACY` for existing non-focus episodes, `FOCUS` for existing focus episodes) and nullable `episodes.run_config_json`, and verify `mvn test` runs the migration successfully against a copy of the production schema
- [x] B.2 Implement `RunConfig` (app defaults + podcast settings + run overrides, resolved once per run) and `RunConfig.resolve`, and verify with unit tests covering all three precedence layers for each overridable field (model per stage, reasoning effort per stage, provider sort/preferred min throughput, target words, research budget, bypass-cache flag)
- [x] B.3 Change `ModelResolver.resolve` to take `RunConfig` instead of `Podcast`, update all call sites, and verify `mvn test` passes with unit tests asserting run overrides take precedence over podcast settings over app defaults
- [x] B.4 Thread `RunConfig` into `OpenRouterRouting`, the composers, and the research stage in place of direct `Podcast` reads, and verify with unit tests that a provider-sort/throughput override changes the request the composer sends
- [x] B.5 Persist the resolved `RunConfig` snapshot to `episodes.run_config_json` at the point `PipelineRunner` finalizes a non-transient run, and verify with a unit test that the persisted snapshot matches the `RunConfig` the run actually used
- [x] B.6 Add `overrides: RunOverrides?` to `RunSpec` and implement the `Sandbox` outcome (no TTS, never publishes, no `lastGeneratedAt` update, no article consumption, epilogue still runs), and verify with a unit test covering each of those four guarantees
- [x] B.7 Add `purpose != 'EXPERIMENT'` filtering to `EpisodeRepository.findLatestCoveringByPodcastId` (`EpisodeRepository.kt:40`) and `EpisodeWindowResolver.findLatestCoveredWindowEnd` (`EpisodeWindowResolver.kt:215`), and verify with unit tests that an experiment episode is ignored by both
- [x] B.8 Add `purpose != 'EXPERIMENT'` filtering to the episode-list query/endpoint and the public feed query, and verify with unit tests that an experiment episode does not appear in either
- [x] B.9 Add `purpose != 'EXPERIMENT'` filtering to the dedup-history lookup and `EpisodeService.hasActiveEpisode`, and verify with unit tests that an experiment episode does not affect dedup history or block a new generation as "active"
- [x] B.10 Add `purpose != 'EXPERIMENT'` handling to `AutoPublishListener`/`PublishingService` so an experiment episode is never auto-published, and verify with a unit test
- [ ] B.11 Run `mvn test`, restart the app, and live-check that a `Sandbox`-outcome run (e.g. by driving `PipelineRunner` with `purpose = EXPERIMENT` manually against a test podcast) does not appear in the episode list, the feed, or affect the schedule

## Phase C: Experiments API, comparison endpoint, two real A/B experiments

- [ ] C.1 Add `app.experiments.max-variant-repeats` config and the pre-flight cost guard rejecting an oversized request before any compose call, and verify with a unit test that an oversized request makes zero `PipelineRunner.run` calls
- [ ] C.2 Implement `POST .../episodes/{id}/experiments` (N variants x k repeats, each a `PipelineRunner` run with `purpose = EXPERIMENT`, `input = ArticleSet` from the source episode, `outcome = Sandbox`), and verify with a MockK unit test that N*k runs are made with the correct overrides
- [ ] C.3 Implement the comparison `GET` endpoint (per-variant judge score, cost, duration, compose call count, reasoning tokens, serving provider), and verify with a unit test against a fixture of completed experiment episodes
- [ ] C.4 Run `mvn test`, restart the app, and live-check the experiment endpoints end to end against a real episode with a small (e.g. 1 variant x 1 repeat) experiment
- [ ] C.5 Run the "compose provider sort by throughput vs. default" A/B experiment via the new API against a real podcast/episode, and record the result in `knowledge/` (entry + index update + `knowledge/log.md`) per the project's knowledge-bundle conventions
- [ ] C.6 Run the "compose reasoning effort low vs. medium" A/B experiment via the new API, and record the result in `knowledge/` (entry + index update + `knowledge/log.md`) per the project's knowledge-bundle conventions
