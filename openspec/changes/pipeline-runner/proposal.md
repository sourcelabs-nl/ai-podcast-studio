## Why

The pipeline today has seven separate entry points into episode generation (`PodcastService.runGenerationPipeline`, `runFocusGenerationPipeline`, `doRetry`, `runFeedbackRecompose`, `runRegeneration`, plus `LlmPipeline.preview`, and the scheduler's call into `generateBriefing`), each with its own copy of stage sequencing, its own `ComposeContext` construction, and its own decision about what happens to the resulting episode (TTS, publish, schedule advance, article consumption). Every one of `ModelResolver`, `OpenRouterRouting`, the composers, and the research stage reads its configuration straight off the `Podcast` entity (`ModelResolver.resolve(podcast, stage)`, `ModelResolver.kt:31-43`, has no override parameter), so there is no way to run the pipeline once with a temporarily different model, reasoning effort, or provider preference without editing the podcast's own settings. That makes A/B testing a prompt or routing change require either a second podcast or a manual, disposable code change, and it makes each of the seven routes a place a future architectural change (like `unify-episode-epilogue`) has to be applied seven times.

## What Changes

- **BREAKING (internal only, no REST contract change)**: introduce `PipelineRunner.run(RunSpec)` as the only way to run the pipeline. `RunSpec` carries a `purpose` (`SCHEDULED`, `MANUAL`, `FOCUS`, `RETRY`, `RERUN`, `REGENERATE`, `RECOMPOSE`, `EXPERIMENT`, `PREVIEW`), an `input` (`Window`, `ArticleSet` from an existing episode, or `Focus(text, window)`), a `resumePoint` (`FULL`, `COMPOSE`, `POST_COMPOSE`), an optional `overrides` (`RunOverrides?`), and an `outcome` describing what happens to the result.
- Introduce four outcome types: `Deliver` (TTS, publish per podcast settings, schedule advance and article consumption for regular episodes), `Review` (stop at `PENDING_REVIEW`), `Sandbox` (no TTS, never publishes, no `lastGeneratedAt` update, no article consumption), `Transient` (runs pipeline stages but persists nothing, replacing today's `preview`).
- Migrate all seven existing routes onto `PipelineRunner`, one at a time, preserving every existing REST endpoint and its observable behavior.
- Introduce `RunConfig` (app defaults + podcast settings + run overrides, resolved once per run) and thread it explicitly into `ModelResolver`, `OpenRouterRouting`, the composers, and the research stage, instead of each re-reading the `Podcast` entity. Overridable: model per stage, reasoning effort per stage, provider sort/preferred min throughput, target words, research budget, bypass-LLM-cache flag. The effective `RunConfig` snapshot is persisted on the episode row.
- Introduce `purpose = EXPERIMENT` episodes: always run with `Sandbox` outcome, always judged, and excluded from episode lists, the public feed, schedule/window resolution, dedup history, active-episode checks, and auto-publish.
- Introduce an experiment API: `POST .../episodes/{id}/experiments` (N variants x k repeats against the source episode's existing article set) and a comparison `GET` endpoint (per-variant judge score, cost, duration, compose call count, reasoning tokens, serving provider).
- Depends on `unify-episode-epilogue`: the shared epilogue built there becomes the runner's post-script step, invoked once per run according to its outcome type (e.g. `Sandbox` still judges but never auto-publishes; `Transient` never invokes it, since there is no persisted script to judge).

## Capabilities

### New Capabilities
- `pipeline-runner`: the `RunSpec`/`PipelineRunner`/`RunConfig` abstraction that replaces the seven ad hoc pipeline entry points, including the `Deliver`/`Review`/`Sandbox`/`Transient` outcome model and per-run configuration overrides.
- `pipeline-experiments`: the experiment API (variants x repeats, comparison endpoint) and the `EXPERIMENT` purpose's exclusion from every listing/scheduling/dedup/publishing surface.

### Modified Capabilities
- `model-registry`: model resolution for a pipeline stage gains a third, highest-precedence layer (a run's own overrides) ahead of the existing podcast-override-then-global-default chain.
- `episode-auto-publishing`: gains the `EXPERIMENT`-purpose exclusion (in addition to the eval-run exclusion from `unify-episode-epilogue`) as a permanent, purpose-based rule.

`episode-regeneration`, `pipeline-retry`, `focus-episode-generation`, and `sse-preview` migrate onto `PipelineRunner` internally (see design.md), but their existing REST contracts, response codes, and documented scenarios are unchanged, so none of them needs a spec delta: the quick test in the specs guidance ("if the implementation can change without changing externally visible behavior, it does not belong in the spec") applies directly.

## Impact

- New files: `PipelineRunner`, `RunSpec`/`RunInput`/`RunOverrides`/`RunOutcome` types, `RunConfig` resolution, experiment REST controller + service.
- `PodcastService.kt`: all seven private `run*`/`doRetry` methods are replaced by calls into `PipelineRunner`, migrated one at a time (Phase A).
- `EpisodeService.kt`: `createEpisodeFromPipelineResult`/`finalizeEpisode` become outcome-specific paths inside (or called by) the runner.
- `ModelResolver.kt`, `OpenRouterRouting.kt`, `ComposeContext.kt`, `LlmPipeline.kt`: take `RunConfig` instead of reading `Podcast` directly (Phase B).
- `EpisodeWindowResolver` (`findLatestCoveredWindowEnd`, `EpisodeWindowResolver.kt:215`) and `EpisodeRepository.findLatestCoveringByPodcastId` (`EpisodeRepository.kt:40`): both must exclude `purpose = EXPERIMENT` episodes (Phase B).
- Database: new migration `V82` (the next free version after `unify-episode-epilogue`'s `V81` lands) adding `episodes.purpose` and `episodes.run_config_json`; `llm_calls` attribution is unchanged (still per-episode).
- Frontend: explicitly out of scope for this change.
