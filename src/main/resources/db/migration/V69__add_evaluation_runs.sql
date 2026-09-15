-- The conditions one evaluation run was composed under, and the episode it produced.
--
-- An ablation compares repetitions of one prompt variant against another, and the composer is not
-- deterministic, so a difference between two sets of scripts is attributable only when the
-- conditions of every run are recorded. A substituted model, a changed temperature, a different
-- variety rotation, a run that replayed a cached answer or a tool that fired in one arm and not the
-- other would each invalidate the comparison silently.
--
-- Separate from the episodes table because a row here exists only for an evaluation run, while an
-- episode exists for every generation, and because the episode's own columns describe the finished
-- artefact rather than the experiment that produced it.
CREATE TABLE evaluation_runs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    episode_id INTEGER NOT NULL REFERENCES episodes(id) ON DELETE CASCADE,
    podcast_id TEXT NOT NULL,
    ran_at TEXT NOT NULL,
    -- SHA-256 of the exact compose prompt, so two runs of "the same" variant can be proven
    -- identical rather than assumed so.
    prompt_hash TEXT NOT NULL,
    variety_selection TEXT NOT NULL,
    compose_model TEXT NOT NULL,
    temperature REAL NOT NULL,
    cache_bypassed INTEGER NOT NULL,
    -- True here means the bypass did not take effect and the run is not an independent sample.
    cache_hit INTEGER NOT NULL,
    tools_fired_json TEXT NOT NULL,
    version INTEGER
);

-- Serves reading an ablation back, newest first, for one podcast.
CREATE INDEX idx_evaluation_runs_podcast ON evaluation_runs(podcast_id, id DESC);
CREATE INDEX idx_evaluation_runs_episode ON evaluation_runs(episode_id);
