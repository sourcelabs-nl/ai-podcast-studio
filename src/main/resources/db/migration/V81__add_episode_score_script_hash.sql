-- SHA-256 of the script text a score was judged against, so a score whose episode has since been
-- rewritten can be recognised as stale and replaced. NULL for every row written before this column
-- existed, which scoring treats as still describing the current script.
ALTER TABLE episode_scores ADD COLUMN script_hash TEXT;
