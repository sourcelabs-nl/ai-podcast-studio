-- The article window an episode was generated for, as ISO-8601 instants.
--
-- Before this, the window was derived at run time from the last published episode's generated_at,
-- so a run could not be reproduced: re-running a past day recomputed the window from the current
-- state instead of the state that day was built on. Storing it makes the run own its input.
--
-- Null for episodes generated before this column existed; those are presented as having an unknown
-- window and are never used as the coverage anchor for a later window.
ALTER TABLE episodes ADD COLUMN window_start TEXT;
ALTER TABLE episodes ADD COLUMN window_end TEXT;

-- Serves the coverage lookup: latest window_end of a podcast's episodes at or before a given end.
CREATE INDEX IF NOT EXISTS idx_episodes_podcast_window_end ON episodes(podcast_id, window_end);
