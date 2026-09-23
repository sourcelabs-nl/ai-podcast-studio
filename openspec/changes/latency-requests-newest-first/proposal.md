## Why

The request list on an episode's latency tab was ordered by duration, so the requests of one run appeared out of time order. Reading a run that timed out and retried (episode 230: a compose call that hit the 20 minute timeout, then a successful retry) was confusing because the list did not show what happened first.

## What Changes

- The request list on the latency tab is ordered by start time, newest first.
- The stage table keeps its p99-descending order.

## Capabilities

### New Capabilities

### Modified Capabilities
- `frontend-llm-latency`: the request list is ordered newest first instead of slowest first.

## Impact

`frontend/src/components/latency-tab.tsx` only. No backend or API change.
