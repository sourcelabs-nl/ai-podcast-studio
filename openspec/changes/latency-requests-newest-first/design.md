## Context

`RequestList` in `frontend/src/components/latency-tab.tsx` sorted requests by duration descending, with cache hits last.

## Goals / Non-Goals

**Goals:** show the requests of an episode in the order they were made, newest first.

**Non-Goals:** changing the stage table order or the API.

## Decisions

Sort by `startedAt` descending with a string comparison; `startedAt` is an ISO-8601 UTC instant, so lexical order is time order.

## Risks / Trade-offs

The slowest request is no longer the first row. The stage table still leads with the slowest stage, and each row shows its duration.
