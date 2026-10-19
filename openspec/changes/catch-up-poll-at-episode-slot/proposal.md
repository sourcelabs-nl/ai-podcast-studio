# Proposal

## Why

When a podcast's slot arrives, generation waits until every source has polled past the window end. Sources poll on their own interval, unrelated to the slot, so an episode due at 16:00 routinely waits until the next regular poll of each source (often 15 to 30 minutes) or until the coverage deadline, even though the content could be fetched immediately.

## What Changes

- When a slot is due and sources are behind the window, the scheduler polls those behind sources itself right away instead of waiting for their regular turn, then re-checks coverage and generates in the same tick when everything is covered.
- Only sources that are behind and healthy are polled: a source whose last poll failed keeps its own backoff schedule and is not polled early.
- Polls of one source never run concurrently: the regular polling loop, the stale-polling catch-up and the slot catch-up share one per-source guard, and a source that was already polled after the request is not polled again.
- The coverage deadline (`app.episode.poll-coverage-deadline-minutes`) stays as the safety net for sources that are still behind after the catch-up.

## Capabilities

### New Capabilities

### Modified Capabilities
- `episode-article-window`: the requirement "Generation waits for the pollers to cover the window" gains an immediate catch-up poll of behind sources before deferring.

## Impact

- `scheduler/BriefingGenerationScheduler`: catch-up step between "due" and the coverage check.
- `scheduler/SourcePollingScheduler`: a method to poll a given list of sources, and a per-source guard shared by every polling path.
- No API, schema or configuration changes.
