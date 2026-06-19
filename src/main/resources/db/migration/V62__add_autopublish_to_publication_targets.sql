-- Per-target opt-in for automatic publishing. Distinct from `enabled` (which gates manual
-- publishing): a target must be both enabled and auto_publish to be published automatically
-- when an episode reaches GENERATED. Existing rows default to 0 (off) so behavior is unchanged.
ALTER TABLE podcast_publication_targets ADD COLUMN auto_publish INTEGER NOT NULL DEFAULT 0;
