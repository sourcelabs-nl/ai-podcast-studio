-- The dedup filter's per-cluster follow-up context, persisted alongside topic and topic_order so a
-- regeneration can recompose with the same [FOLLOW-UP: ...] annotations the original compose saw.
-- Without it a regeneration had no continuity signal and fell back on the searchPastEpisodes tool,
-- which demoted a launch story on a keyword match against an unrelated older story.
ALTER TABLE episode_articles ADD COLUMN follow_up_context TEXT;
