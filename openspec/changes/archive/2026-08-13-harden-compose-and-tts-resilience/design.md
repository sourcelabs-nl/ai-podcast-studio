## Context

The compose stage feeds every deduped article into a single LLM call with deep-dive web-search and history tools. When many articles survive (62 on a busy day), the call exceeds the 10-minute request timeout and the episode fails. Retrying such an episode resumes from compose (`ResumePoint.COMPOSE`), reloading the persisted articles and skipping dedup, so any cap placed only in dedup would not bound the retry path.

Inworld TTS synthesizes chunks in parallel, each via `synthesizeWithRetry`. `InworldApiClient.handleError` mapped HTTP 401 → credentials error, 429 → retryable `InworldRateLimitException`, and everything else → a non-retryable `IllegalStateException`. A transient HTTP 503 therefore failed a chunk immediately, failing the whole episode.

## Goals / Non-Goals

**Goals:**
- Bound compose input on all entry paths (scheduled run, full-pipeline retry, retry-from-compose).
- Give large composes enough time to finish.
- Let a brief Inworld 5xx outage self-heal without failing the episode.

**Non-Goals:**
- Per-chunk audio caching so a retry re-synthesizes only failed chunks (deferred; a full retry still re-synthesizes the whole script).
- Per-podcast override of the compose article cap (global config only).
- Changing the compose model, dedup, or scoring behavior.

## Decisions

- **Cap where the linking is derived AND at the shared chokepoint.** A `capForCompose` helper sorts by `relevanceScore` descending and takes `compose.maxArticles`. It runs in `dedup()` (so follow-up annotations, topic labels, token totals, and episode-article links all derive from the capped set for new episodes) and again at the top of `compose()` (idempotent; bounds retry-from-compose, which bypasses dedup). Default cap is 40.
- **Dropped articles stay unprocessed.** They are not linked to the episode, remain eligible, and are cleared by the existing age gate once the next episode publishes, so they neither pollute dedup lookback nor accumulate indefinitely.
- **Timeout 10 → 20 min** in `ChatClientFactory` for the compose client only; dedup/filter stages bound their own output via `maxTokens`.
- **New `InworldTransientException` for HTTP 5xx.** `handleError` maps 500–599 to it; `synthesizeWithRetry` catches it with the same 3-attempt 1s/2s/4s backoff already used for 429 and `ResourceAccessException`. A distinct type keeps 5xx semantics separate from rate limiting and I/O.

## Risks / Trade-offs

- **Reduced breadth on busy days:** capping at 40 drops the lowest-relevance overflow. Acceptable: those articles carry no unique high-value signal and the target word count already compresses tail articles into rapid-fire.
- **20-min timeout ties up a request longer** on a genuinely stuck call, but the tool budget and non-blocking pipeline bound the blast radius; a degenerate call still eventually fails rather than hanging forever.
- **5xx retry adds up to ~7s of backoff per failing chunk** before giving up. Acceptable versus failing the whole episode; persistent 5xx still fails after 3 attempts.
