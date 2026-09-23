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

**1. `RunSpec` is a sealed-friendly data class, not a sealed class, for `input`.** `RunInput` is the sealed type (`Window`, `ArticleSet`, `Focus`), while `RunSpec` itself stays a plain data class with `purpose: RunPurpose`, `input: RunInput`, `resumePoint: ResumePoint`, `overrides: RunOverrides?`, `outcome: RunOutcome`. This mirrors the project's existing style of a small sealed hierarchy for a "kind of thing" field rather than sealing the entire container (compare `ComposeContext`, a plain data class, vs. e.g. a hypothetical sealed pipeline-result type).

**2. `PipelineRunner` is a new `@Service` in the `llm` package that depends on the same collaborators `PodcastService`/`LlmPipeline` already depend on (`ModelResolver`, research, the composers) plus `EpisodeService`'s epilogue.** It does not replace `LlmPipeline`; `LlmPipeline` keeps owning the actual stage implementations (aggregate/score/filter, dedup, compose), and `PipelineRunner` owns sequencing them per `RunSpec` and applying the outcome. This keeps the stage logic itself, which `unify-episode-epilogue` does not touch, stable across both changes.

**3. Phase A migrates routes in this order, each behind its own task/test/live-check:** regeneration first (already the most self-contained: composition-only, no dedup/scoring re-run), then retry, then focus generation + feedback recompose, then scheduled/manual generation (the most complex, most-used path, migrated last so the runner has already proven itself on lower-traffic routes), then preview. This order minimizes the blast radius of an early mistake: a runner bug hit through regeneration affects a rarely-used admin action, while the same bug hit through scheduled generation affects every podcast's daily episode.

  *Alternative considered*: migrate scheduled generation first, since it is "the main path" and de-risking it early seems attractive. Rejected: it is also the path with the most existing behavior to preserve exactly (SSE progress events, schedule advancement, article consumption, auto-publish), so it is the worst candidate for finding runner bugs against.

**4. `RunConfig` is resolved by a single new function `RunConfig.resolve(appDefaults, podcast, overrides)` that produces one immutable value per run, computed once at the top of `PipelineRunner.run` and threaded through every call it makes into `ModelResolver`, the composers, and the research stage.** `ModelResolver.resolve` gains a `runConfig: RunConfig` parameter (replacing its `podcast: Podcast` parameter, since every field it reads from `Podcast` today is now available, already-layered, on `RunConfig`). Composers and the research stage receive `RunConfig` the same way they receive `ComposeContext` today (as an explicit parameter), rather than looking it up from a shared/thread-local run identity, keeping with this project's preference for explicit parameters over implicit context.

**5. The config snapshot is persisted as `episodes.run_config_json` (a JSON blob of the resolved `RunConfig`), not as individual columns.** `RunConfig`'s field set is expected to grow as more of the pipeline becomes overridable, and a JSON blob avoids a migration per new overridable field, matching the existing precedent of `Podcast.llmModels`/`composeSettings` already being stored as JSON rather than columns.

**6. `episodes.purpose` is a plain string column (not a foreign key or enum type, consistent with the existing SQLite/Spring Data JDBC conventions in this codebase, e.g. `EpisodeStatus` stored as a string).** Every episode gets a purpose from this change forward; a pre-existing episode's `purpose` is backfilled to `SCHEDULED` or `MANUAL`... in practice the migration cannot distinguish which, so it backfills every pre-existing non-focus episode to a new sentinel `LEGACY` purpose value that behaves identically to `SCHEDULED`/`MANUAL` everywhere (i.e. is never excluded from anything), and every pre-existing focus episode to `FOCUS`.

**7. Purpose-based exclusion (`EXPERIMENT`) is implemented as a `purpose NOT IN (...)`/`purpose != 'EXPERIMENT'` clause added to the existing queries** (`EpisodeRepository.findLatestCoveringByPodcastId`, `EpisodeWindowResolver.findLatestCoveredWindowEnd`, the episode-list query, the feed query, dedup-history lookups, `hasActiveEpisode`), rather than a separate "excluding experiments" wrapper repository. This is the smallest change to each call site and matches how `EpisodeStatus` filtering is already done inline in this codebase's repository queries.

**8. The experiment cost guard is a configured `app.experiments.max-variant-repeats` (variants x repeats), checked in the experiment service before any `PipelineRunner.run` call is made.** A hard cap rather than a soft warning, per the proposal's explicit call for one; the exact number is an implementation/ops decision left to the config default, not a spec-level requirement.

## Risks / Trade-offs

- [Phase A touches every generation route; a runner bug could regress a route that was working under the old code.] → Mitigated by the migration order (Decision 3: lowest-traffic first) and by requiring a test plus a live check per migrated route (see tasks.md), rather than migrating all seven at once.
- [Experiments must never leak into feed/schedule/dedup/auto-publish.] → Enforcement points are enumerated in Decision 7 and each gets its own task and test: `EpisodeRepository.findLatestCoveringByPodcastId` (`EpisodeRepository.kt:40`), `EpisodeWindowResolver.findLatestCoveredWindowEnd` (`EpisodeWindowResolver.kt:215`), the episode-list endpoint's query, the public feed's query, whatever dedup-history query feeds `LlmPipeline.dedup`, `EpisodeService.hasActiveEpisode`, and `AutoPublishListener`/`PublishingService`. Missing any one of these is the primary failure mode of Phase B/C, so each is a named task rather than folded into a general "add purpose filtering" task.
- [Cost risk: N variants x k repeats is unbounded by default.] → The cost guard (Decision 8) caps it before any model call; the cap is a hard rejection, not a warning, per the proposal.
- [`run_config_json` as a blob makes it harder to query "which episodes used model X" than a column would.] → Accepted: this mirrors `llmModels`/`composeSettings`'s existing precedent, and the alternative (a column per overridable field, migrated repeatedly) is worse for this project's stated preference against unnecessary migrations.

## Migration Plan

**Phase A:**
1. Add `RunSpec`/`RunInput`/`RunOverrides`/`RunOutcome` types and a `PipelineRunner` skeleton that only supports `REGENERATE` (delegating everything else back to the old `PodcastService` code paths).
2. Migrate regeneration onto it; test + live-check.
3. Migrate retry; test + live-check.
4. Migrate focus generation + feedback recompose; test + live-check.
5. Migrate scheduled/manual generation; test + live-check.
6. Migrate preview (`Transient` outcome); test + live-check.
7. Delete the now-dead `PodcastService` private methods this replaced.

**Phase B:**
1. Add migration for `episodes.purpose` and `episodes.run_config_json`, with the `LEGACY`/`FOCUS` backfill from Decision 6.
2. Implement `RunConfig.resolve` and thread it through `ModelResolver`, `OpenRouterRouting`, composers, research.
3. Persist the resolved `RunConfig` snapshot on the episode.
4. Implement `Sandbox` outcome.
5. Add `purpose`-based filtering at every enforcement point listed in the Risks section, each with its own test.

**Phase C:**
1. Add the experiment REST endpoints and cost guard.
2. Add the comparison endpoint.
3. Run the two real A/B experiments named in the proposal (compose provider sort by throughput vs. default; compose reasoning effort low vs. medium) and record results in `knowledge/` per the project's knowledge-bundle conventions.

**Rollback**: each phase's migration adds nullable/backfilled columns only; reverting code at any point leaves the schema in a state the previous code version already tolerates (it simply never reads the new columns).

## Open Questions

- The exact default for `app.experiments.max-variant-repeats` is an operational choice, not a spec-level requirement, and can be set (or changed) after Phase C ships without affecting the design or task breakdown.
