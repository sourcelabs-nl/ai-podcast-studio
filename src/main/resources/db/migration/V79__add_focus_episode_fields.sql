-- A focus episode is a one-off episode scoped to a free-text focus instead of the podcast's topic.
-- `focus` holds that text (NULL for a regular episode); `review_feedback` holds the latest feedback a
-- reviewer submitted to recompose its script.
ALTER TABLE episodes ADD COLUMN focus TEXT;
ALTER TABLE episodes ADD COLUMN review_feedback TEXT;

-- The web searches a focus episode's compose stage ran, one row per result, so the review screen can
-- show which sources the script drew on.
CREATE TABLE episode_research_sources (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    episode_id INTEGER NOT NULL REFERENCES episodes(id) ON DELETE CASCADE,
    query TEXT NOT NULL,
    title TEXT NOT NULL,
    url TEXT NOT NULL,
    ordinal INTEGER NOT NULL
);

CREATE INDEX idx_episode_research_sources_episode_id ON episode_research_sources(episode_id);
