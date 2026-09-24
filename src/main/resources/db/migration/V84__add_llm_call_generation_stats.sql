-- OpenRouter's own account of each request, fetched from GET /api/v1/generation after the request
-- returned. duration_ms says how long a request took; these say where that time went: ttft_ms is
-- the time until the first token (queueing plus prompt processing), generation_time_ms the time
-- spent generating, and provider_attempts_json every upstream endpoint OpenRouter tried, in order,
-- as a JSON array of {provider, status, latencyMs}.
--
-- The native token counts are the provider's own, which differ from the normalised input_tokens and
-- output_tokens (117 native against 153 normalised completion tokens on one probe), and output
-- speed has to be computed from them.
--
-- generation_id is null for cache hits, failed requests and non-OpenRouter providers. The stats
-- arrive 10-20 seconds after the request, so a row can hold a generation_id and no stats yet;
-- provider_attempts_json is written with every successful lookup and marks that stats are present.
ALTER TABLE llm_calls ADD COLUMN generation_id TEXT;
ALTER TABLE llm_calls ADD COLUMN ttft_ms INTEGER;
ALTER TABLE llm_calls ADD COLUMN generation_time_ms INTEGER;
ALTER TABLE llm_calls ADD COLUMN native_completion_tokens INTEGER;
ALTER TABLE llm_calls ADD COLUMN native_reasoning_tokens INTEGER;
ALTER TABLE llm_calls ADD COLUMN finish_reason TEXT;
ALTER TABLE llm_calls ADD COLUMN provider_attempts_json TEXT;
