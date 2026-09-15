-- One row per LLM request, with how long that request took.
--
-- The stage timeouts in application.yaml are okhttp callTimeouts, which apply per HTTP request,
-- while everything the application measured until now was wall-clock over a whole stage: the
-- composers time their entire call() and the scoring stage times a loop over every article. With
-- Spring AI's internal tool execution a single compose invocation issues several requests, so the
-- stage figure can be several times the largest request it contains. Sizing a per-request ceiling
-- on it is guesswork, which is what this table replaces.
--
-- Separate from llm_cache because that table is keyed on (prompt_hash, model) and deduplicates: an
-- identical second call is a hit and writes no row there, so it can never count invocations. It is
-- also separate from the token and cost totals on articles and episodes, which are sums per article
-- and per stage and cannot express one request.
CREATE TABLE llm_calls (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    started_at TEXT NOT NULL,
    stage TEXT NOT NULL,
    provider TEXT NOT NULL,
    model TEXT NOT NULL,
    duration_ms INTEGER NOT NULL,
    input_tokens INTEGER NOT NULL,
    output_tokens INTEGER NOT NULL,
    -- Provider-reported cost (USD), absent when the provider reports none.
    reported_cost_usd REAL,
    -- A cache hit performs no network request, so its duration is not a latency and percentiles
    -- exclude it. Recorded rather than skipped so the hit rate stays visible.
    cache_hit INTEGER NOT NULL,
    -- 'ok' or 'error'. A request that hit its timeout reports the timeout's duration rather than the
    -- provider's, so percentiles exclude errors too.
    outcome TEXT NOT NULL,
    -- Exception class for a failed call, to tell a timeout apart from an upstream 5xx.
    error_type TEXT,
    version INTEGER
);

-- Serves both reads this table has: percentiles over a recent window, and the retention delete.
CREATE INDEX idx_llm_calls_started_at ON llm_calls(started_at);
