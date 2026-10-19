# Design

## Context

`BriefingGenerationScheduler.checkAndGenerate` runs every minute. It finds the next slot after the latest window end an episode served (`PodcastService.findLatestServedWindowEnd`, falling back to `lastGeneratedAt`, then start of day), skips earlier-day slots and applies the retry gate (`isRetryAllowed`: one automatic retry five minutes after a failure, none after two). For a due slot it resolves the window, calls `ensureFreshPolling` (polls all sources of the podcast only when the whole polling loop has gone stale) and then `isWindowCovered`, which defers while `SourceService.findSourcesBehindWindow` returns sources that will poll again before the deadline. `SourcePollingScheduler` owns all polling: `pollSources` (the minute loop) and `pollPodcastSourcesNow` (stale catch-up), both via `pollHostGroup`, which goes through the host circuit breaker and applies per-host delays. The two schedulers run in separate coroutine scopes, so a source can today be polled by both at once. Every source belongs to exactly one podcast.

## Goals / Non-Goals

**Goals:**
- Generate within a minute or two of the slot when all sources can be fetched.
- One polling code path, one per-source guard.

**Non-Goals:**
- Aligning regular poll schedules with slots.
- Polling sources in backoff early, or bypassing the host circuit breaker.
- Deduplicating polls across podcasts (sources are never shared).

## Decisions

**Catch-up lives in the generation flow, polling lives in `SourcePollingScheduler`.** A new `pollSourcesNow(podcastId, sources)` reuses `pollHostGroup` (breaker, host delays, eager scoring stays in the loop). `pollPodcastSourcesNow` becomes a thin call to it with all enabled sources. Alternative, a separate poller component: rejected, it would duplicate breaker, backoff and delay handling.

**Which sources:** `findSourcesBehindWindow` minus those with `consecutiveFailures > 0`. Healthy behind sources are exactly the ones a poll can bring into coverage; failing ones stay on backoff and are already handled by the existing "not worth waiting for" filter and the deadline.

**When:** once per tick, after `ensureFreshPolling` and before `isWindowCovered` (so after the retry gate, which keeps a failed slot from triggering catch-ups every minute), only while the deadline has not passed. No extra state is needed to avoid repeat catch-ups: a successful poll moves `lastPolled` past the window end so the source is no longer behind, and a failed poll sets `consecutiveFailures`, which excludes it.

**Per-source guard:** a `ConcurrentHashMap<String, Mutex>` in `SourcePollingScheduler`, taken around each source poll in `pollHostGroup`. After acquiring, the source is re-read through `SourceService.findById` (the scheduler never touches a repository) and a catch-up skips it when `lastPolled` is at or after the moment the catch-up was requested. The regular loop skips it when it is no longer due. `SourcePoller` writes `lastPolled` when a poll finishes, so a poll that completed while the catch-up waited for the lock is always seen. A source that no longer exists is skipped. Alternative, a single global lock: rejected, it would serialize unrelated hosts.

**Ordering with stale catch-up:** `ensureFreshPolling` stays first. After a stale catch-up nothing is behind and the slot catch-up is a no-op.

## Risks / Trade-offs

- [The catch-up blocks the generation loop for other podcasts while it runs, host delays included] → Only behind sources of one podcast are polled; this is the same trade-off `ensureFreshPolling` already makes.
- [Polling a source a few minutes earlier than its interval adds load on that host] → At most one extra poll per source per slot, through the circuit breaker.
- [Mutex map grows with source ids] → Bounded by the number of sources; entries are tiny.
