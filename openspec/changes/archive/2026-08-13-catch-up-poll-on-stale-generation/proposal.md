## Why

The source poller and the briefing generator are two independent 60-second loops. When the machine sleeps through a podcast's scheduled time and then wakes, the generation loop can fire the overdue cron before the polling loop has caught up, so the episode is composed against stale data. This produced episode 141 of "The Daily Agentic AI Podcast": it generated ~83 minutes late (off its 15:00 slot) with a single eligible article, while ~50 relevant articles for that day were only polled and scored afterwards.

## What Changes

- `SourcePollingScheduler` records the completion time of each poll round (`lastPollRoundCompletedAt`, in memory). After a restart it is `null`, which correctly signals "freshness unknown".
- `SourcePollingScheduler` exposes an on-demand `pollPodcastSourcesNow(podcastId)` that polls a single podcast's enabled sources to completion (reusing the existing host-group poll logic), plus a blocking wrapper for callers outside a coroutine.
- Before generating a briefing, `BriefingGenerationScheduler` checks whether the last poll round is stale (older than a configurable threshold, or never recorded). If so, it runs a synchronous catch-up poll of that podcast's sources first, then proceeds. The freshly polled posts flow through the existing aggregate → score → dedup → compose stages.
- New config `app.source.stale-round-threshold-minutes` (default 10) controls the staleness threshold.

## Capabilities

### Modified Capabilities
- `source-polling`: the poller now tracks the last completed poll round and supports an on-demand single-podcast poll.
- `podcast-pipeline`: scheduled generation runs a catch-up poll first when polling is stale (e.g. after the machine was asleep), so episodes are not composed against stale data.

## Impact

- **Modified**: `SourcePollingScheduler.kt` (round timestamp + `pollPodcastSourcesNow`), `BriefingGenerationScheduler.kt` (staleness guard + catch-up poll), `AppProperties.kt` + `application.yaml` (threshold), `BriefingGenerationSchedulerTest.kt` / `SourcePollingSchedulerTest.kt`.
- No schema changes. The round timestamp is in-memory only (transient by design).
- Trade-off: a generation that follows downtime is delayed by one catch-up poll round (bounded by the podcast's source count and per-host poll delays). Normal on-cadence generation is unaffected.
