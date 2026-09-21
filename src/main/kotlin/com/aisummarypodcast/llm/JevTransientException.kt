package com.aisummarypodcast.llm

/**
 * A Jev failure worth another attempt: an upstream 5xx, a rate limit, or the endpoint's own
 * `529 system_overloaded`.
 *
 * Retrying is worth more here than for most clients this project has, because the decisions
 * endpoint is alpha, served by one provider, and has no OpenRouter routing fallback behind it.
 * In a single morning it returned `503 no healthy upstream` to three consecutive requests and
 * `529 system_overloaded` to both chunks of a live pipeline run, while serving healthy requests
 * in under a second either side of those.
 *
 * Only transient statuses raise this. A rejected request (`400 max_tokens_exceeded` for an
 * oversized batch, or a bad credential) would fail identically on every attempt, so it is left to
 * the caller's ordinary failure path and yields no answers at once.
 */
class JevTransientException(message: String) : RuntimeException(message)
