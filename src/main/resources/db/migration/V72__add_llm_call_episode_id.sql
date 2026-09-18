-- Which episode an LLM request was issued for.
--
-- V70 recorded requests without attribution, which left the latency figures readable only as an
-- aggregate over a time window. That answers "how slow are compose requests lately" while the
-- question people actually ask is "why was this episode slow", and on an episode's page a window
-- aggregate is read as that episode's timings.
--
-- The episode row already exists before every stage runs: the generation pipeline creates it before
-- scoring, and the eval stage runs against a finished episode. The attribution was left out rather
-- than ruled out.
--
-- Nullable because preview runs and ad-hoc source scoring issue real requests with no episode to
-- name. NULL there is the truthful answer, not missing data. Existing rows keep NULL and are not
-- backfilled: nothing links them to an episode, and timestamp proximity would be a guess.
ALTER TABLE llm_calls ADD COLUMN episode_id INTEGER;

-- Serves both per-episode reads: the percentiles, which group by stage, and the request list.
CREATE INDEX idx_llm_calls_episode ON llm_calls(episode_id, stage);
