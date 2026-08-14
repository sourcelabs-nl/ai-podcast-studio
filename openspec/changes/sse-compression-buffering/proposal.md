## Why

No progress ever reaches the browser from either Server-Sent Events stream: the preview pipeline and the user event stream. The cause is transport, not parsing or emission. The Next.js server gzip-compresses the proxied response, and gzip holds bytes back until its buffer fills, so a stream of small events delivers nothing until the run ends and the buffer is flushed.

Measured against the running app: a request to the event stream direct to the backend on port 8085 returns a heartbeat immediately. The same request through the dashboard on port 3005 returns nothing for 20 seconds. Repeating it with `Accept-Encoding: identity` returns the heartbeat immediately. Browsers always send `Accept-Encoding: gzip`, so the browser always takes the buffered path.

This sat underneath an earlier fix to the preview client's SSE parsing. That fix was necessary and correct, but it could not have any visible effect while the events were never arriving.

## What Changes

- The Next.js server no longer compresses responses, so proxied event streams reach the browser as they are produced.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `frontend-upcoming-episode`: preview progress reaches the browser while the pipeline runs.

## Impact

- `frontend/next.config.ts`.
- Both proxied SSE streams benefit: the preview progress stream and the user event stream that drives episode notifications.
- Responses are no longer gzipped. The payloads are small and the dashboard is served locally, so the bandwidth cost is immaterial against losing live progress.
- No backend, API contract, schema, or cost change.
