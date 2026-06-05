## Context

Episode 139 ('The Daily Agentic AI Podcast') failed audio generation twice on 2026-06-05. The first attempt failed with `Operation timed out`, the manual retry failed with `Connection reset` only 37ms after the parallel chunk fan-out started. The reset pattern (failure milliseconds after the request, against `api.inworld.ai` on GCP) is stale keep-alive connection reuse: Reactor Netty's shared global connection pool held connections idle for ~12 minutes that Inworld's load balancer had already closed server-side.

`InworldTtsProvider.synthesizeWithRetry` had a retry loop, but it only caught `InworldRateLimitException` (HTTP 429). Any `ResourceAccessException` (connection reset, timeout) propagated on the first attempt and failed the entire episode pipeline.

This is a retrofit design: the implementation is complete and verified.

## Goals / Non-Goals

**Goals:**
- Survive transient I/O failures on individual TTS chunk requests without failing the episode
- Stop reusing pooled connections that the server has already closed

**Non-Goals:**
- Retrying non-transient errors (401, 4xx/5xx other than 429) — these still fail fast
- Changing the pipeline-level retry behavior in `PodcastService`
- Applying the same treatment to other providers (ElevenLabs) — no observed failures there

## Decisions

1. **Broaden `synthesizeWithRetry` to also catch `ResourceAccessException`**, reusing the existing 1s/2s/4s backoff schedule and `MAX_RETRY_ATTEMPTS = 3`. Alternative considered: a separate retry policy per error type — rejected as unnecessary complexity; transient I/O and rate limits warrant the same backoff. A fresh attempt opens a new connection (the stale one is discarded on failure), so a single retry typically succeeds.

2. **Named `ConnectionProvider("inworld-tts")` with `maxIdleTime = 30s`** in `InworldApiClient`, held as a class-level `val` so the pool is shared across requests rather than created per call. Alternative considered: `HttpClient.create()` with the global default pool plus `disableRetry`/`keepAlive(false)` — rejected; disabling keep-alive costs a TLS handshake per request, while a 30s idle eviction keeps pooling benefits during the parallel fan-out (5 concurrent requests) and only evicts between runs. 30s is comfortably below typical GCP load balancer idle timeouts.

## Risks / Trade-offs

- [Retrying a request the server may have partially processed] → TTS synthesis is idempotent and read-only billing-wise per processed character; a duplicate attempt only re-bills the characters of the retried chunk, bounded by 3 attempts.
- [30s idle time too aggressive] → Slightly more reconnects between distant runs; negligible cost (one TLS handshake) versus a failed episode.
