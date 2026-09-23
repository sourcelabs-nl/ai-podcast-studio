-- Which experiment an EXPERIMENT episode belongs to: the experiment's id (one POST to the
-- experiments endpoint), the variant it ran under, and the episode whose article set it recomposes.
-- All NULL for every other episode. The comparison endpoint reads experiments by source episode.
ALTER TABLE episodes ADD COLUMN experiment_id TEXT;
ALTER TABLE episodes ADD COLUMN experiment_variant TEXT;
ALTER TABLE episodes ADD COLUMN experiment_source_episode_id INTEGER REFERENCES episodes(id) ON DELETE SET NULL;
CREATE INDEX idx_episodes_experiment_source_episode_id ON episodes(experiment_source_episode_id);
