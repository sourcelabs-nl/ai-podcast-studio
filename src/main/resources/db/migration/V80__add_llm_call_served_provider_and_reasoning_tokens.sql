-- The upstream provider OpenRouter routed each request to, and how many of its output tokens were
-- reasoning. Both are NULL where the response did not report them (a cache hit, a failed request,
-- a provider that does not report them, and every row written before this column existed).
ALTER TABLE llm_calls ADD COLUMN served_provider TEXT;
ALTER TABLE llm_calls ADD COLUMN reasoning_tokens INTEGER;
