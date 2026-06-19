-- Optional per-podcast gate requiring explicit approval before an episode can be published,
-- mirroring require_review (which gates audio generation). When enabled, a GENERATED episode
-- must be approved for publication before the Publish action is allowed.
ALTER TABLE podcasts ADD COLUMN require_publish_approval INTEGER NOT NULL DEFAULT 0;

-- Whether an episode has been approved for publication. Existing episodes default to 1 (approved)
-- so they remain publishable; new episodes under require_publish_approval are created with 0.
ALTER TABLE episodes ADD COLUMN publish_approved INTEGER NOT NULL DEFAULT 1;
