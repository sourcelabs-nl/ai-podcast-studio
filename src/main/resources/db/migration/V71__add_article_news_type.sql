-- How the scoring stage classified an article in time: DEVELOPMENT, RETROSPECTIVE or EVERGREEN.
-- See com.aisummarypodcast.llm.NewsType for what each value means.
--
-- Nullable, and null means "not classified". Every row that exists when this runs was scored by a
-- prompt that never asked the question, and a null is kept by the eligibility filter rather than
-- dropped, so back-filling would mean guessing on content nobody classified. Articles scored from
-- here on carry a value.
ALTER TABLE articles ADD COLUMN news_type TEXT;
