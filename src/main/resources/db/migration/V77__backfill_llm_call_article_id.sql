-- Links historical scoring requests to the article they scored.
--
-- V74 added the column and left existing rows NULL because nothing linked them to an article. That
-- is no longer true: the article carries the exact figures its scoring call returned
-- (llm_input_tokens, llm_output_tokens, llm_reported_cost_usd), so the two can be matched on what
-- the provider actually stated rather than on a timestamp guess.
--
-- The match is required to be unique in both directions, and the article must not already be
-- claimed by another request. A wrong link moves real money onto the wrong episode, which is worse
-- than the gap it would close; an unmatched request stays NULL and its episode's score stage keeps
-- reading its persisted column, which is what happens today.
--
-- Measured on the live database at the time of writing: 1086 of 1643 eligible requests match
-- uniquely. Episode 226 reaches 151 of its 178 candidates, so its score stage stays on its column
-- while its dedup and gate stages, which name their episode directly, project.
--
-- Only successful requests are considered. A failed request returned no tokens, so it would match
-- every article that also has none.
WITH candidate AS (
    SELECT c.id AS call_id, a.id AS article_id
    FROM llm_calls c
    JOIN articles a
      ON a.llm_input_tokens = c.input_tokens
     AND a.llm_output_tokens = c.output_tokens
     AND ((a.llm_reported_cost_usd IS NULL AND c.reported_cost_usd IS NULL)
          OR a.llm_reported_cost_usd = c.reported_cost_usd)
    WHERE c.stage = 'filter'
      AND c.article_id IS NULL
      AND c.outcome = 'ok'
),
unambiguous AS (
    SELECT call_id, article_id
    FROM candidate
    WHERE call_id IN (SELECT call_id FROM candidate GROUP BY call_id HAVING COUNT(DISTINCT article_id) = 1)
      AND article_id IN (SELECT article_id FROM candidate GROUP BY article_id HAVING COUNT(DISTINCT call_id) = 1)
      AND article_id NOT IN (SELECT article_id FROM llm_calls WHERE article_id IS NOT NULL)
)
UPDATE llm_calls
SET article_id = (SELECT article_id FROM unambiguous WHERE call_id = llm_calls.id)
WHERE id IN (SELECT call_id FROM unambiguous);
