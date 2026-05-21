-- Per-stage LLM cost breakdown on episodes.
-- Splits the aggregate (llm_input_tokens, llm_output_tokens, llm_cost_cents) into
-- four stages: scoring (Stage 1), dedup (Stage 3), compose (Stage 4), recap.
-- The aggregate columns continue to exist and are maintained as the sum of the
-- four stages by EpisodeService — single write path, never updated independently.

ALTER TABLE episodes ADD COLUMN score_input_tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE episodes ADD COLUMN score_output_tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE episodes ADD COLUMN score_cost_cents INTEGER NOT NULL DEFAULT 0;

ALTER TABLE episodes ADD COLUMN dedup_input_tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE episodes ADD COLUMN dedup_output_tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE episodes ADD COLUMN dedup_cost_cents INTEGER NOT NULL DEFAULT 0;

ALTER TABLE episodes ADD COLUMN compose_input_tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE episodes ADD COLUMN compose_output_tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE episodes ADD COLUMN compose_cost_cents INTEGER NOT NULL DEFAULT 0;

ALTER TABLE episodes ADD COLUMN recap_input_tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE episodes ADD COLUMN recap_output_tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE episodes ADD COLUMN recap_cost_cents INTEGER NOT NULL DEFAULT 0;

-- Backfill score_input_tokens / score_output_tokens from per-article token counts
-- linked via episode_articles. Cost cents are NOT backfilled: the per-article
-- llm_cost_cents column rounds sub-cent scoring calls to 0, so summing it gives
-- a meaningless zero. Going forward, EpisodeService computes score_cost_cents
-- from the SUM of tokens (not the sum of per-article costs) to preserve
-- precision. Pre-V57 episodes keep 0 for all four stage cost-cent columns; the
-- frontend renders a "detailed breakdown not available" notice when stage
-- cost cells are all 0 but the aggregate llm_cost_cents is non-zero.
UPDATE episodes SET
    score_input_tokens = COALESCE((
        SELECT SUM(a.llm_input_tokens)
        FROM episode_articles ea
        JOIN articles a ON ea.article_id = a.id
        WHERE ea.episode_id = episodes.id
    ), 0),
    score_output_tokens = COALESCE((
        SELECT SUM(a.llm_output_tokens)
        FROM episode_articles ea
        JOIN articles a ON ea.article_id = a.id
        WHERE ea.episode_id = episodes.id
    ), 0);
