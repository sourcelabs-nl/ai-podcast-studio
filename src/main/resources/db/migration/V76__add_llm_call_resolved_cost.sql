-- What one LLM request cost, recorded with the request itself.
--
-- Until now a cost was resolved at the end of a pipeline stage and written only to the episode's
-- per-stage columns, inside the transaction that carries the stage's results. A run that pays for
-- its calls and then fails before that write leaves no cost behind: episode 226 issued a dedup and
-- a dedup-gate call on its first run, failed on the save, and reported only the retry's calls.
-- The request row survives such a failure (it is written in its own transaction), so the cost
-- belongs on it.
--
-- reported_cost_usd stays what the provider stated. resolved_cost_usd is what the call is counted
-- as: the reported value where there is one, the configured per-Mtok rates otherwise, and NULL
-- where neither was available. NULL is the truthful answer there, because a zero is
-- indistinguishable from a call that genuinely cost nothing.
--
-- USD, matching reported_cost_usd, so the two values of one call are comparable without converting
-- either; the conversion to fractional cents happens once where the breakdown is served.
ALTER TABLE llm_calls ADD COLUMN resolved_cost_usd REAL;

-- Which of those the value came from, using the same vocabulary as episodes.llm_cost_source:
-- API, API_CACHED, TABLE or UNKNOWN. MIXED is an aggregate and never appears on a single call.
ALTER TABLE llm_calls ADD COLUMN cost_source TEXT;

-- Existing rows keep NULL in both. A cost that was never resolved cannot be reconstructed from a
-- rate table that has since changed, and inventing one would enter the ledger as a fact.
