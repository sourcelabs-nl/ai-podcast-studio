## Why

On busy news days dozens of articles survive scoring and dedup, and composing all of them in one LLM call runs past the compose request timeout, failing the episode. Separately, a brief Inworld server-side outage (HTTP 5xx) during TTS fails the whole episode instead of self-healing, because 5xx responses were not treated as retryable. Both surfaced as production episode failures.

## What Changes

- Cap the number of articles fed into a single compose request to the highest-relevance top-N (configurable, default 40). Articles beyond the cap are dropped from that episode (kept unprocessed; the age gate clears them once the next episode publishes). The cap is enforced at the shared compose chokepoint so every entry path is bounded, including retry-from-compose which reloads previously persisted articles and skips dedup.
- Raise the compose LLM request timeout from 10 to 20 minutes so a large, deep-dive compose has headroom to finish.
- Treat Inworld TTS HTTP 5xx (500–599) responses as transient and retry them per chunk with the existing exponential backoff (3 attempts, 1s/2s/4s). Previously only HTTP 429 and I/O errors were retried; 5xx failed immediately.

## Capabilities

### New Capabilities
<!-- None. -->

### Modified Capabilities
- `podcast-pipeline`: adds a requirement bounding compose input to a configurable top-N-by-relevance cap and setting the compose request timeout.
- `inworld-tts`: modifies the API error-handling requirement so HTTP 5xx is retried transiently instead of failing immediately.

## Impact

- `LlmPipeline` (compose article cap at the shared chokepoint and in dedup for consistent linking), `AppProperties.ComposeProperties` (new `maxArticles`), `application.yaml` (`llm`-sibling `compose.max-articles`), `ChatClientFactory` (compose timeout 10 → 20 min).
- `InworldApiClient.handleError` (map 5xx → new `InworldTransientException`), `InworldTtsProvider.synthesizeWithRetry` (retry the new exception), new `InworldTransientException`.
- No API, schema, or migration changes. No breaking changes.
