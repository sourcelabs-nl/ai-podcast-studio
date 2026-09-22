-- Carries the provider's own figure across into the resolved cost of the requests that already have
-- one.
--
-- V76 left every existing row's resolved cost NULL on the grounds that a cost which was never
-- resolved cannot be reconstructed. That holds for a row costed from a rate table which has since
-- changed, and not for a row where the provider stated what the call cost: reported_cost_usd is the
-- charge itself, and copying it resolves nothing, it only puts a fact that is already recorded where
-- the projection reads it.
--
-- Without this every historical request counts as a cost that could not be resolved, which holds
-- each of its stages on its persisted column: episode 226, whose first run paid for a dedup and a
-- dedup-gate call and then failed, would keep under-reporting the $0.0129 this change exists to
-- recover, although all six of its requests carry a reported cost.
--
-- A cache hit replayed a cost rather than being charged one, which the episode-level vocabulary
-- already distinguishes. Rows with no reported cost stay NULL: for those the rate table is the only
-- other source, and what it said at the time is gone.
UPDATE llm_calls
SET resolved_cost_usd = reported_cost_usd,
    cost_source = CASE WHEN cache_hit = 1 THEN 'API_CACHED' ELSE 'API' END
WHERE reported_cost_usd IS NOT NULL
  AND resolved_cost_usd IS NULL;
