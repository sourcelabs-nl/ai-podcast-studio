-- The calendar date (YYYY-MM-DD) on which the development an article reports happened, as stated by
-- the content and returned by the scoring stage. It differs from published_at when a feed publishes
-- or republishes a write-up of an older development.
--
-- Nullable, and null means "no stated date". Rows scored before this column existed carry none, and
-- the eligibility filter keeps an article without a date rather than guessing.
ALTER TABLE articles ADD COLUMN development_date TEXT;
