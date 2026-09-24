-- OpenRouter's generation `latency` is not the time to the first token. Measured on a compose
-- request (2026-09-24): latency 130,538 ms, generation_time 159,954 ms for a 160,582 ms request,
-- while the served endpoint's own latency in provider_responses was 1,777 ms. `latency` is the time
-- until the first answer token, after the model's reasoning, and generation_time spans the whole
-- request. The column is renamed for what it holds; the time until the provider started responding
-- is read from provider_attempts_json.
ALTER TABLE llm_calls RENAME COLUMN ttft_ms TO first_content_ms;
