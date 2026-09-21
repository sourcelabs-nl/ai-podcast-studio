-- The dedup stage's already-covered gate, costed as a stage of its own.
--
-- The gate runs a different model at different rates against a different question, and its charge
-- was added to the dedup amount before that amount was stored, leaving it indistinguishable from
-- the clustering call it relieves. The latency report has told the two apart under 'dedup' and
-- 'dedup-gate' since V70; these columns let the costs breakdown do the same.
--
-- The call count is stored rather than derived. The other single-call stages report 1 when they
-- have tokens, but the gate chunks its candidates and retries transient failures, so its request
-- count does not follow from its tokens.
--
-- Existing episodes read 0 and are not backfilled: their dedup amount holds both charges with no
-- record of the split, and apportioning it would be a guess.
ALTER TABLE episodes ADD COLUMN dedup_gate_input_tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE episodes ADD COLUMN dedup_gate_output_tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE episodes ADD COLUMN dedup_gate_cost_cents INTEGER NOT NULL DEFAULT 0;
ALTER TABLE episodes ADD COLUMN dedup_gate_calls INTEGER NOT NULL DEFAULT 0;

-- Nullable like its siblings: null means no provider-reported cost contributed, so the read path
-- recomputes from tokens and the configured rate.
ALTER TABLE episodes ADD COLUMN dedup_gate_reported_cost_cents REAL;
