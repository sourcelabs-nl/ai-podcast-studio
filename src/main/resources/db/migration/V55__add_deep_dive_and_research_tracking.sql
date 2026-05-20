-- Deep-dive research feature: opt-in per-podcast flag, episode cost tracking,
-- and a query-keyed cache for Tavily responses so identical re-runs are free
-- and deterministic.

ALTER TABLE podcasts ADD COLUMN deep_dive_enabled INTEGER NOT NULL DEFAULT 0;

ALTER TABLE episodes ADD COLUMN research_calls INTEGER NOT NULL DEFAULT 0;
ALTER TABLE episodes ADD COLUMN research_cost_cents INTEGER;

CREATE TABLE research_cache (
    query_hash TEXT NOT NULL,
    max_results INTEGER NOT NULL,
    query TEXT NOT NULL,
    response_json TEXT NOT NULL,
    cached_at TEXT NOT NULL,
    PRIMARY KEY (query_hash, max_results)
);
