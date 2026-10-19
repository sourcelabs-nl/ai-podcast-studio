-- When an episode was marked FAILED. generated_at is the creation time, and a run can fail long after
-- it, so the scheduler's retry delay needs its own timestamp.
--
-- Nullable: rows that failed before this column existed carry none, and the scheduler treats them
-- as having failed long enough ago.
ALTER TABLE episodes ADD COLUMN failed_at TEXT;
