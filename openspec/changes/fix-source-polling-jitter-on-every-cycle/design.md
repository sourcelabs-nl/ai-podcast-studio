## Context

`SourcePollingScheduler` runs a coroutine loop that calls `pollSources()` every 60 seconds. On each invocation it called `applyStartupJitter(allSources)`, which for every source with `lastPolled == null` writes a synthetic `lastPolled = now - random(0..pollIntervalMinutes)`. This was originally intended to spread the first wave of polls after application restart so all sources don't fire simultaneously.

The unintended consequence: any source added at runtime starts with `lastPolled == null`, gets a synthetic timestamp on the next 60s cycle, and then `isDue()` returns false until `pollIntervalMinutes` has elapsed. Users observed that newly-added sources were only polled after a process restart (which itself was just another iteration where the synthetic timestamp had aged into the past).

## Goals / Non-Goals

**Goals:**
- Newly-added sources are picked up by the polling loop within one cycle (~60s) of being created.
- Startup jitter remains in effect for the first cycle after `ApplicationReadyEvent` so the initial wave of polls is spread across the configured interval.

**Non-Goals:**
- Rework of the polling cadence, backoff, or host-grouping logic.
- Any persistence schema change.
- Adding admin/manual triggers to force-poll a source.

## Decisions

**Gate jitter on a `firstCycle` flag inside the scheduler.**

The scheduler holds a `private var firstCycle = true`. On the first call to `pollSources()` it applies jitter and sets `firstCycle = false`. Subsequent cycles skip jitter entirely. Sources added later therefore retain `lastPolled = null`, which makes `isDue()` return `true` immediately on the next cycle.

Alternatives considered:
- *Apply jitter only when `lastPolled == null` AND the source's `createdAt` is older than process start.* Adds a clock dependency for a corner case (sources created in the same millisecond as startup). The `firstCycle` flag is simpler and equivalent in practice.
- *Have `SourceService.createSource` set `lastPolled` to a past instant.* Spreads the policy across two components; the scheduler is the right owner of polling cadence.
- *Remove jitter entirely.* Loses the legitimate startup-stampede protection.

## Risks / Trade-offs

- [If many sources are added simultaneously at runtime, they all become due on the next cycle and poll back-to-back within their host groups.] → Existing per-host serialization and `pollDelaySeconds` already pace requests to the same host; cross-host concurrency is the same as today.
- [If the scheduler bean is recreated (e.g., during a hot reload in a dev profile), `firstCycle` resets and jitter runs again.] → Acceptable; the dev scenario is already a quasi-restart.
