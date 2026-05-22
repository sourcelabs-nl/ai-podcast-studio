-- Caps the number of rapid-fire items per episode so the closing segment has
-- room to explain each story instead of being crammed into multi-item turns.
-- Nullable: when null, the composer falls back to compose.rapid-fire-max-items
-- from application.yaml.

ALTER TABLE podcasts ADD COLUMN rapid_fire_max_items INTEGER;
