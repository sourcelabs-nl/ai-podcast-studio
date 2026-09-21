-- Which article an LLM request was issued for.
--
-- V72 attributed a request to its episode, which leaves the scoring stage unattributable: an
-- article is scored when it arrives, days before the episode that uses it exists, so a scoring
-- request carries no episode and an episode's request list reports a scoring stage that issued
-- nothing. Naming the article lets those requests be gathered through the episode's candidates.
--
-- Nullable because most requests are issued for no single article: compose, recap, the dedup
-- clustering call and the gate all cover a whole set. NULL there is the truthful answer, not
-- missing data. Existing rows keep NULL and are not backfilled; nothing links them to an article.
ALTER TABLE llm_calls ADD COLUMN article_id INTEGER;

-- Serves the per-episode request list, which gathers the scoring requests of the episode's
-- candidate articles.
CREATE INDEX idx_llm_calls_article ON llm_calls(article_id);
