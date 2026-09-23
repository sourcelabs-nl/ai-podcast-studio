-- Why an episode's script was produced (EpisodePurpose enum names) and the resolved run
-- configuration it was produced with (a JSON snapshot of RunConfig). Existing episodes cannot tell a
-- scheduled run from a manual one, so they are LEGACY, which is excluded from nothing; existing
-- focus episodes are FOCUS. run_config_json stays NULL for every episode written before it existed.
ALTER TABLE episodes ADD COLUMN purpose TEXT NOT NULL DEFAULT 'LEGACY';
UPDATE episodes SET purpose = 'FOCUS' WHERE focus IS NOT NULL;
ALTER TABLE episodes ADD COLUMN run_config_json TEXT;
