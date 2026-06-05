## Why

Episode audio generation fails outright when a single Inworld TTS request hits a transient I/O error (`Connection reset`, `Operation timed out`). Episode 139 failed twice on 2026-06-05: the retry loop only covered HTTP 429 rate limits, so a connection reset on the first attempt killed the whole pipeline. The resets are caused by Reactor Netty reusing stale pooled keep-alive connections that Inworld's load balancer already closed server-side.

## What Changes

- `InworldTtsProvider.synthesizeWithRetry` now also retries `ResourceAccessException` (connection reset, timeout) using the existing 1s/2s/4s backoff, in addition to `InworldRateLimitException`.
- `InworldApiClient` uses a named Reactor Netty `ConnectionProvider` (`inworld-tts`) with `maxIdleTime` of 30 seconds, so idle pooled connections are evicted before the server closes them and stale connections are never reused.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `inworld-tts`: The "Inworld API error handling" requirement gains transient I/O error retry behavior; the "Inworld API client with Basic authentication" requirement gains connection pool idle eviction.

## Impact

- `src/main/kotlin/com/aisummarypodcast/tts/InworldTtsProvider.kt` (retry loop)
- `src/main/kotlin/com/aisummarypodcast/tts/InworldApiClient.kt` (connection provider)
- No API, schema, or dependency changes. Retrofit change: implementation is already complete and verified (874 tests passing).
