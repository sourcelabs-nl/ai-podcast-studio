# Proposal

## Why

At the cron slot the scheduler defers generation while any enabled source is behind the window, for at most 30 minutes. A source that failed repeatedly is in exponential backoff and will not poll again for hours, so waiting for it can only delay the episode. The daily episode of 2 and 1 October started 30 minutes after the slot for this reason: one RSS source returned 502 and sat in a four-hour backoff.

## What Changes

- Only a source that will be polled again before the coverage deadline holds generation back. A source whose next poll falls after the deadline no longer defers it.
- When generation goes ahead without waiting, the sources that are behind are named at WARN, as they are after the deadline today.
- `Source` exposes when it is next due (`nextPollAt`), which the due check already computed internally.

The definition of "behind" is unchanged.

## Capabilities

### New Capabilities

### Modified Capabilities
- `episode-article-window`: a source that cannot poll before the coverage deadline does not defer generation.

## Impact

- `scheduler/BriefingGenerationScheduler.kt`, `store/Source.kt`, and their tests.
