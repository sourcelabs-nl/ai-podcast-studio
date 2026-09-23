## Context

See `unify-episode-epilogue`'s design for the current shape of the seven routes and their epilogue duplication; that change lands first and its epilogue becomes this runner's post-script step (`Deliver`/`Review`/`Sandbox` outcomes invoke it, `Transient` does not).

Today, configuration flows one way only: `ModelResolver.resolve(podcast, stage)` (`ModelResolver.kt:31-43`) reads `podcast.llmModels` directly, `OpenRouterRouting.extraBodyFor` (`OpenRouterRouting.kt:69-86`) is called per-request with whatever provider/effort the caller already resolved, and `ComposeContext` (`ComposeContext.kt:46-58`) is built fresh by each route with no shared notion of "this run's configuration." There is no `RunConfig` type and no override mechanism anywhere in the codebase today; every field a podcast can set for a stage is the only value that stage can ever run with.

`Episode` (per `EpisodeService.kt` usage) has no `purpose` column and no config snapshot column; the closest existing thing is `EvaluationRun` (`EvaluationRunRecorder.kt`), which records provenance for `bypassLlmCache=true` runs specifically, keyed off the episode id, and is not a general run-identity mechanism.

`EpisodeWindowResolver.findLatestCoveredWindowEnd` (`EpisodeWindowResolver.kt:215`) and `EpisodeRepository.findLatestCoveringByPodcastId` (`EpisodeRepository.kt:40`) both query episodes for a podcast with no purpose filter; today that is correct because every episode is either a real one or a focus one, neither of which should be excluded from schedule resolution the way an experiment must be.

## Goals / Non-Goals

**Goals:**
- Single call surface (`PipelineRunner.run`) for every pipeline invocation, with existing REST endpoints, response codes and documented behaviors unchanged.
- `RunConfig` as an explicit, passed-in value everywhere a stage currently reads `Podcast` for its own configuration.
- Experiments that are cheap to reason about because they are Sandbox-outcome, EXPERIMENT-purpose, `PipelineRunner` runs like any other, not a separate code path.
- Migration in small, independently testable, independently deployable steps (Phase A migrates one route at a time; nothing about Phase A depends on Phase B or C existing yet).

**Non-Goals:**
- Frontend work for experiments (explicitly deferred to a later change).
- Removing or renaming any REST endpoint.
- Building a general "run history" browsing UI; the comparison endpoint in Phase C is the only new read surface.
- Changing how `llm_calls` attributes cost (stays per-episode, per the proposal's Impact section).

## Decisions

**1. `RunSpec` is a plain data class; `RunInput`, `RunOutcome` and `RunResult` are small sealed types (`podcast/RunSpecTypes.kt`).** `RunSpec` carries `podcast`, the target `episode` (null only for a `Transient` run), `purpose: RunPurpose`, `input: RunInput` (`Window`, `ArticleSet` carrying the source episode's `LinkedArticlesResult`, `Focus(text, window)`), `resumePoint` (the existing `ResumePoint` enum), `outcome: RunOutcome` and `bypassLlmCache`. `RunOutcome` is `Deliver(updateLastGenerated, generatedAt)` (finalize per podcast settings, which stops a focus episode or a review-requiring podcast at `PENDING_REVIEW`), `Review(feedback)` (a feedback recompose rewriting an episode that stays in review) and `Transient(onProgress)`. `RunResult` is `Completed`, `NothingToCompose`, `Failed` or `Previewed`. Phase B adds `overrides: RunOverrides?` and the `Sandbox` outcome together with the code that reads them, and moves `bypassLlmCache` into `RunConfig`; they do not exist as unused placeholders before then.

**2. `PipelineRunner` is a new `@Service` in the `podcast` package (`podcast/PipelineRunner.kt`) that depends on `LlmPipeline`, `EpisodeService`, `EpisodeWindowResolver` and the event publisher.** It lives in `podcast`, not `llm`, because it depends on `EpisodeService`, `PodcastEvent` and `ResumePoint`, all `podcast` types; putting it in `llm` would make `llm` import from `podcast`, inverting the dependency direction the rest of the codebase keeps (`podcast` depends on `llm`, never the reverse). `llm` still exposes the few data types `podcast` needs (`EpisodeWindow`, `EpisodeWindowResolver`, `SupportedLanguage`), which is the same import surface `llm` had before this change. It does not replace `LlmPipeline`; `LlmPipeline` keeps owning the stage implementations (aggregate/score/filter, dedup, compose, recompose, and the preview stages), and `PipelineRunner` owns sequencing them per `RunSpec`, progress reporting, failure handling, and applying the outcome. The outcome is where the shared epilogue runs: `Deliver` through `EpisodeService.finalizeEpisode`/`createEpisodeFromPipelineResult`, `Review` through `EpisodeService.runEpilogueForRewrite`; `Transient` never reaches it. `PodcastService` keeps each route's validation, placeholder-episode creation and the background `pipelineScope` launch, and builds the `RunSpec`.

  Route-specific behavior that predates the runner is preserved as purpose-based rules inside it: a `RETRY` from the full pipeline passes no recent focus episodes to the prompt, emits no `dedup_saved`/`script_saved`/`marking_processed`/`generating_recap` events, and fails (rather than deletes) its episode when nothing is eligible; a `REGENERATE` emits stage events without persisting the stage; a failed `Review` run clears the stage and emits `episode.recompose_failed` instead of failing the episode; a `Transient` run lets its failure propagate to the preview endpoint, which reports it. Every `Deliver` path honours `outcome.updateLastGenerated` at finalize, including the resume-at-compose and focus paths, not only regeneration's `createEpisodeFromPipelineResult`; a `RERUN` builds `Deliver(updateLastGenerated = false)`, so re-running a past window no longer advances the podcast's schedule (a pre-existing bug, since `RERUN` already created its placeholder episode with `updateLastGenerated = false` but the runner ignored the outcome's flag at finalize).

**3. Phase A migrates routes lowest-risk first, each pinned by tests before and after the move:** preview (`Transient`, persists no episode), then re-run, regeneration, feedback recompose, focus generation, retry, and finally scheduled/manual generation (the most complex, most-used path, migrated last so the runner has already proven itself on lower-traffic routes). A runner bug hit through an early route affects a rarely-used action, while the same bug hit through scheduled generation affects every podcast's daily episode.

  *Alternative considered*: migrate scheduled generation first, since it is "the main path" and de-risking it early seems attractive. Rejected: it is also the path with the most existing behavior to preserve exactly (SSE progress events, schedule advancement, article consumption, auto-publish), so it is the worst candidate for finding runner bugs against.

  The preview's `Transient` outcome persists no episode, episode-article link or episode score. Its stages still aggregate unlinked posts and relevance-score unscored articles, which are shared article state every run produces the same way, exactly as the preview did before the runner. The preview's window is resolved by the route before the run starts and passed to `LlmPipeline.preview`.

**4. `RunConfig` is resolved by a single new function `RunConfig.resolve(appDefaults, podcast, overrides)` that produces one immutable value per run, computed once at the top of `PipelineRunner.run` and threaded through every call it makes into `ModelResolver`, the composers, and the research stage.** `ModelResolver.resolve` gains a `runConfig: RunConfig` parameter (replacing its `podcast: Podcast` parameter, since every field it reads from `Podcast` today is now available, already-layered, on `RunConfig`). Composers and the research stage receive `RunConfig` explicitly rather than looking it up from a shared/thread-local run identity, keeping with this project's preference for explicit parameters over implicit context: the composers through `ComposeContext.runConfig` (which the pipeline fills from the podcast's own configuration when a caller passes none), the research stage through `ResearchRequest.runConfig`, and the selection stages (`aggregateScoreAndFilter`, `selectForFocus`, `scoreForFocus`, `dedup`, `preview`) as a parameter.

  `RunConfig` and `RunOverrides` live in `llm/RunConfigTypes.kt`, since `ModelResolver` in `llm` reads them. `RunConfig` holds a model and a reasoning effort for every stage, the OpenRouter `ProviderPreferences` (`sort`, `preferredMinThroughput`), `targetWords`, `researchQueryCap` (null keeps the research stage's own cap) and `bypassLlmCache`; `RunOverrides` holds the same fields, each optional. Compose reasons at the podcast's `composeSettings.reasoningEffort` or `app.compose.reasoning-effort`; every other stage defaults to `none`, exactly the efforts the stages sent before. `ModelResolver.resolve(runConfig, stage)` puts the stage's effort and the provider preferences on the `ResolvedModel`, and every request is built with `withRoutingAndReasoning(model)`, so a stage cannot send a different effort from the one the run resolved. `OpenRouterRouting.extraBodyFor` adds `provider.sort` and `provider.preferred_min_throughput` (field names per OpenRouter's provider-routing documentation) next to the quantization floor and `require_parameters` only when set, so a run without overrides sends the same request body as before. Work outside a run (eager scoring, the recap, the judge, the scoring spend estimate) resolves `RunConfig.resolve(appProperties, podcast)` with no overrides.

**5. The config snapshot is persisted as `episodes.run_config_json` (a JSON blob of the resolved `RunConfig`), not as individual columns.** `PipelineRunner` records it, with the episode's `purpose`, through `EpisodeService.recordRun` before the first stage of every run that writes an episode, so a failed run and an experiment still in flight carry both; a retry or recompose overwrites them with its own. `RunConfig`'s field set is expected to grow as more of the pipeline becomes overridable, and a JSON blob avoids a migration per new overridable field, matching the existing precedent of `Podcast.llmModels`/`composeSettings` already being stored as JSON rather than columns.

**6. `episodes.purpose` is a plain string column (not a foreign key or enum type, consistent with the existing SQLite/Spring Data JDBC conventions in this codebase, e.g. `EpisodeStatus` stored as a string).** Every episode gets a purpose from this change forward; a pre-existing episode's `purpose` is backfilled to `SCHEDULED` or `MANUAL`... in practice the migration cannot distinguish which, so it backfills every pre-existing non-focus episode to a new sentinel `LEGACY` purpose value that behaves identically to `SCHEDULED`/`MANUAL` everywhere (i.e. is never excluded from anything), and every pre-existing focus episode to `FOCUS`.

**6a. The `Sandbox` outcome applies only to an `ArticleSet` input** (`RunSpec` rejects any other), since an experiment recomposes an existing episode's article set. `EpisodeService.finalizeSandboxEpisode` stores the script, costs and article links and runs the epilogue, but generates no audio, leaves `publishApproved` false, publishes no episode event, consumes no articles and leaves `lastGeneratedAt` alone; a failed experiment does not move the schedule either (`failEpisode` skips an `EXPERIMENT` episode as it skips a focus one). `PublishingService.publish` refuses an `EXPERIMENT` episode and `AutoPublishListener` skips it.

**7. Purpose-based exclusion (`EXPERIMENT`) is implemented as a `purpose NOT IN (...)`/`purpose != 'EXPERIMENT'` clause added to the existing queries** (`EpisodeRepository.findLatestCoveringByPodcastId`, `EpisodeWindowResolver.findLatestCoveredWindowEnd`, the episode-list query, the feed query, dedup-history lookups, `hasActiveEpisode`), rather than a separate "excluding experiments" wrapper repository. This is the smallest change to each call site and matches how `EpisodeStatus` filtering is already done inline in this codebase's repository queries.

**8. The experiment cost guard is a configured `app.experiments.max-variant-repeats` (variants x repeats), checked in the experiment service before any `PipelineRunner.run` call is made.** A hard cap rather than a soft warning, per the proposal's explicit call for one; the exact number is an implementation/ops decision left to the config default, not a spec-level requirement.

## Risks / Trade-offs

- [Phase A touches every generation route; a runner bug could regress a route that was working under the old code.] → Mitigated by the migration order (Decision 3: lowest-traffic first) and by requiring a test plus a live check per migrated route (see tasks.md), rather than migrating all seven at once.
- [Experiments must never leak into feed/schedule/dedup/auto-publish.] → Enforcement points are enumerated in Decision 7 and each gets its own task and test: `EpisodeRepository.findLatestCoveringByPodcastId` (`EpisodeRepository.kt:40`), `EpisodeWindowResolver.findLatestCoveredWindowEnd` (`EpisodeWindowResolver.kt:215`), the episode-list endpoint's query, the public feed's query, whatever dedup-history query feeds `LlmPipeline.dedup`, `EpisodeService.hasActiveEpisode`, and `AutoPublishListener`/`PublishingService`. Missing any one of these is the primary failure mode of Phase B/C, so each is a named task rather than folded into a general "add purpose filtering" task.
- [Cost risk: N variants x k repeats is unbounded by default.] → The cost guard (Decision 8) caps it before any model call; the cap is a hard rejection, not a warning, per the proposal.
- [`run_config_json` as a blob makes it harder to query "which episodes used model X" than a column would.] → Accepted: this mirrors `llmModels`/`composeSettings`'s existing precedent, and the alternative (a column per overridable field, migrated repeatedly) is worse for this project's stated preference against unnecessary migrations.

## Migration Plan

**Phase A:**
1. Add `RunSpec`/`RunInput`/`RunOutcome`/`RunResult` types and `PipelineRunner`.
2. Migrate the routes onto it in the Decision 3 order, each with tests pinning its behavior and a full `mvn clean test` before the next.
3. Delete the `PodcastService` private methods the runner replaced.
4. Live-check the preview endpoint; the other routes are covered by tests and the next scheduled run.

**Phase B:**
1. Add migration for `episodes.purpose` and `episodes.run_config_json`, with the `LEGACY`/`FOCUS` backfill from Decision 6.
2. Implement `RunConfig.resolve` and thread it through `ModelResolver`, `OpenRouterRouting`, composers, research.
3. Persist the resolved `RunConfig` snapshot on the episode.
4. Add `overrides: RunOverrides?` to `RunSpec` and implement the `Sandbox` outcome.
5. Add `purpose`-based filtering at every enforcement point listed in the Risks section, each with its own test.

**Phase C:**
1. Add the experiment REST endpoints and cost guard.
2. Add the comparison endpoint.
3. Run the two real A/B experiments named in the proposal (compose provider sort by throughput vs. default; compose reasoning effort low vs. medium) and record results in `knowledge/` per the project's knowledge-bundle conventions.

**Rollback**: each phase's migration adds nullable/backfilled columns only; reverting code at any point leaves the schema in a state the previous code version already tolerates (it simply never reads the new columns).

## Open Questions

- The exact default for `app.experiments.max-variant-repeats` is an operational choice, not a spec-level requirement, and can be set (or changed) after Phase C ships without affecting the design or task breakdown.
