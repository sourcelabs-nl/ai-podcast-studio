-- Judged attention scores, one row per episode per scorer version.
--
-- Separate from the episodes table on purpose. The episode's own cost columns describe what it cost
-- to produce that episode; scoring happens afterwards and may happen many times, so folding it in
-- would corrupt both the per-episode economics and the cost gate.
--
-- The scorer version and judge model are stored because a judged score is not stable: a change to
-- the judge prompt or to the model changes what the number means, and a comparison that mixed two
-- of them would average quantities that are not the same quantity.
CREATE TABLE episode_scores (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    episode_id INTEGER NOT NULL REFERENCES episodes(id) ON DELETE CASCADE,
    scorer_version INTEGER NOT NULL,
    judge_model TEXT NOT NULL,
    scored_at TEXT NOT NULL,
    -- The derived overall score in [0, 1], plus the components it is the mean of. All computed in
    -- Kotlin from the judge's turn anchors, never asked of the model.
    overall REAL NOT NULL,
    cliffhanger_score REAL NOT NULL,
    humor_score REAL NOT NULL,
    teaser_score REAL NOT NULL,
    promises INTEGER NOT NULL,
    deferred_promises INTEGER NOT NULL,
    unpaid_promises INTEGER NOT NULL,
    median_deferral_turns INTEGER,
    humor_beats INTEGER NOT NULL,
    humor_speaker_balance REAL NOT NULL,
    humor_reaction_ratio REAL NOT NULL,
    teaser_topics INTEGER NOT NULL,
    -- The raw anchors as returned, so a stored score can be audited against the script later
    -- without paying for the judge call again.
    anchors_json TEXT NOT NULL,
    input_tokens INTEGER NOT NULL,
    output_tokens INTEGER NOT NULL,
    cost_cents INTEGER,
    version INTEGER
);

-- Serves the skip check: an episode already scored at the current version is not scored again.
CREATE UNIQUE INDEX idx_episode_scores_episode_version ON episode_scores(episode_id, scorer_version);
