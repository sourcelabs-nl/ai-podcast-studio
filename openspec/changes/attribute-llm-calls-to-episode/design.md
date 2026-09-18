## Context

See proposal.md - Why for the motivation.

What shapes the approach:

- `CachingChatModel` is the single chokepoint every `ChatModel.call()` passes through, and it is
  where the telemetry row is written. It is constructed fresh per call site by
  `ChatClientFactory.buildCachingModel`, so per-generation data can be given to it at construction
  without being shared between generations.
- `LlmCallLogService.record` is `@Transactional(REQUIRES_NEW)`, which is synchronous and same-thread.
  Nothing between the call and the write hops threads.
- The composers do hop: they issue the request inside `withContext(Dispatchers.IO)`. Anything held
  in thread-bound state before that boundary does not arrive after it.
- The codebase has no thread-local, MDC or request-scoped context convention. There is nothing to
  extend, so any ambient mechanism would be a new pattern introduced for one field.
- The episode row exists before every stage: `PodcastService.runGenerationPipeline` calls
  `episodeService.createGeneratingEpisode` first, and `EpisodeScoringService` operates on persisted
  episodes. The id is already in the caller's frame at every call site.
- `llm_calls` is written at roughly one row per article scored, so a few hundred rows per episode
  per day, bounded by the existing retention window.

## Goals / Non-Goals

**Goals:**

- One nullable column and one index carry the attribution; no second table and no join.
- The id travels the existing call path as an ordinary parameter, visible at every hop.
- The windowed query keeps working unchanged when no episode is named, so the existing endpoint and
  any caller of it are unaffected.

**Non-Goals:**

- Backfilling attribution onto existing rows. It cannot be derived: nothing links an existing row
  to an episode, and timestamp proximity would guess.
- Replacing the episode cost aggregates with sums over these rows. The rows now carry episode,
  tokens and cost, which makes that derivable, but it is a separate change.
- Attributing to anything finer than an episode, such as the article a scoring request was for.

## Decisions

### Explicit parameter rather than ambient context

The id is added to `LlmCallRecord` and to `CachingChatModel`'s constructor, and is passed down from
the call sites through `ChatClientFactory`.

The alternative, a thread-local or coroutine `ThreadContextElement` set once by the orchestrator,
touches fewer files. It was rejected on two grounds. A plain thread-local is not merely untidy here
but wrong: the composers cross a dispatcher boundary, so it would silently record `NULL` for exactly
the stage the tab is most often opened for, and the failure is invisible. A coroutine context
element would work, but it introduces an ambient-context mechanism the codebase does not otherwise
have, to carry one field along a path that already passes `ComposeContext` and `ResolvedModel`
explicitly.

The cost is a parameter on several signatures. That is the honest shape of the change: these call
sites genuinely do know which episode they are working on.

### The id is a constructor parameter on `CachingChatModel`, not a field on `ResolvedModel`

`ResolvedModel` describes which model a stage resolves to. It is configuration-derived and says
nothing about a run. Putting a per-generation id on it would make a config object carry runtime
state, and anything caching or comparing `ResolvedModel` would silently become per-episode.

`buildCachingModel` already constructs a new `CachingChatModel` per call site, so a constructor
parameter is per-generation without any sharing concern.

### The id enters each stage by its existing context object where one exists

- Compose: `ComposeContext` gains `episodeId`. It already carries `episodeDate`, so per-episode
  facts are what it is for.
- Eval: `ScriptJudge.judge` gains an `episodeId` parameter. Its only caller,
  `EpisodeScoringService`, has the full `Episode`.
- Filter and dedup: `LlmPipeline` passes the id from `PodcastService.runGenerationPipeline`.

`ChatClientFactory.createForModel` and `createForCompose` take `episodeId: Long? = null`. The
default is what keeps preview, ad-hoc scoring and every other non-episode caller compiling and
correct: they record no episode because they have none.

### The episode query drops the time window rather than combining with it

When an episode is named, `started_at >= cutoff` is not applied. An episode is a bounded set of
requests; intersecting it with a window could only hide some of them, and would make the answer
depend on when the page was opened. The retention window still bounds what exists at all, so an old
enough episode reports nothing either way.

### "Predates attribution" is derived, not configured

The frontend must tell an episode that issued nothing from one generated before attribution existed.
Rather than a configured cutoff date or a flag on the episode, the backend compares the episode's
generation time against `MIN(started_at)` over rows that carry an episode. Below that point, no row
could have been attributed regardless of what happened.

This is self-maintaining, needs no deploy-time constant, and degrades correctly: before the first
attributed row exists, every episode reads as predating, which is true.

### Percentiles are reused, not reimplemented

`latencyPercentilesSince` gains an optional episode and applies it to both the grouping query and
the nearest-rank offset query. The exact-percentile approach is kept: at an episode's volume it is
trivially affordable, and approximating over eleven samples would be absurd.

## Risks / Trade-offs

- **Every episode generated before this change shows an empty tab** → Unavoidable, since attribution
  cannot be backfilled. Mitigated by saying so in the view rather than showing a zero.
- **Several signatures grow a parameter, and a new call site can forget to pass it** → The result is
  a `NULL` episode, which is the same as the honest answer for a path that has none, so a forgotten
  id degrades to invisible rather than wrong. Accepted: the alternative that removes this risk is
  the ambient context rejected above, whose failure mode is worse.
- **Percentiles over a handful of requests invite over-reading** → The sample count is already
  required to be shown, and the request list beneath makes the small n concrete rather than implied.
- **Scoring attribution inflates an episode's request count relative to intuition** → An episode
  scores many articles, most of which do not appear in it. The stage labels already distinguish
  Scoring from Compose, so the count is attributable to the stage that produced it.

## Migration Plan

`V72` adds `episode_id INTEGER` (nullable) and an index on `(episode_id, stage)`. Adding a nullable
column with no default rewrites nothing in SQLite and needs no backfill.

Rollback is the reverse of the deploy with no data step: the column is nullable and nothing reads it
unless an episode is named, so an older application binary runs against the migrated schema
unchanged.
