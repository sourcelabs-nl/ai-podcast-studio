-- Provider-reported LLM cost. OpenRouter returns its authoritative charge (USD) inline in the
-- usage object; capturing it replaces the hand-maintained per-Mtok estimate wherever it is present.
-- All columns are nullable with no default so rows written before this change stay distinguishable
-- from rows written after it. No backfill: a reported cost that was never captured cannot be
-- reconstructed.

-- Cost reported for the cached call, replayed on a cache hit so a cached stage keeps the cost it
-- had when it actually ran. Legacy rows are null and replay token counts only.
ALTER TABLE llm_cache ADD COLUMN reported_cost_usd REAL;

-- Cost reported for the article's scoring call. The score stage makes one call per article, so the
-- stage total is aggregated from these per-article values.
ALTER TABLE articles ADD COLUMN llm_reported_cost_usd REAL;

-- Where the episode's LLM cost came from: API, API_CACHED, TABLE, MIXED or UNKNOWN. Null for
-- episodes generated before this column existed; those are presented as estimates.
ALTER TABLE episodes ADD COLUMN llm_cost_source TEXT;

-- Per-stage reported cost, in fractional CENTS (not USD, unlike the two columns above) so the value
-- is stored exactly as the cost breakdown serves it and needs no second conversion. Written only
-- when a provider-reported cost contributed to the stage (source API, API_CACHED or MIXED); null
-- otherwise, so the stage falls back to recomputing from tokens and the configured rates.
ALTER TABLE episodes ADD COLUMN score_reported_cost_cents REAL;
ALTER TABLE episodes ADD COLUMN dedup_reported_cost_cents REAL;
ALTER TABLE episodes ADD COLUMN compose_reported_cost_cents REAL;
ALTER TABLE episodes ADD COLUMN recap_reported_cost_cents REAL;
