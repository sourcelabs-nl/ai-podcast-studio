-- Every article that stood as a candidate for an episode, and what became of it.
--
-- An episode was charged only for the articles it used. Scoring runs against an article's full
-- body, the most expensive input the pipeline sends, and a run that scored 188 candidates to
-- compose 121 attributed the other 67 to nothing. They were scored because they fell in this
-- episode's window, judged against this episode's covered topics, and dropped by this episode's
-- dedup stage.
--
-- Separate from episode_articles rather than a column on it. That table answers "which articles
-- does this episode cover" and is read on those terms by the show notes, the sources file, the
-- recap, the topic ordering and the marking of articles as processed. A dropped candidate must
-- reach none of them.
--
-- UNIQUE(episode_id, article_id) holds the rule the windows already guarantee: EpisodeWindowResolver
-- starts each window where the previous one ended, so an article falls inside exactly one window and
-- stands as a candidate for exactly one episode. A bug that scored it twice fails here rather than
-- doubling a cost.
CREATE TABLE episode_candidate_articles (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    episode_id INTEGER NOT NULL REFERENCES episodes(id) ON DELETE CASCADE,
    article_id INTEGER NOT NULL REFERENCES articles(id) ON DELETE CASCADE,
    -- USED, EXCLUDED_BY_GATE, DROPPED_AS_DUPLICATE, CUT_BY_COMPOSE_CAP
    outcome TEXT NOT NULL,
    UNIQUE(episode_id, article_id)
);

-- The costs breakdown reads an episode's candidates; the request list reads them to gather the
-- scoring requests, which name the article and no episode.
CREATE INDEX idx_episode_candidates_episode ON episode_candidate_articles(episode_id);
CREATE INDEX idx_episode_candidates_article ON episode_candidate_articles(article_id);
