-- Weighted subtopics feature: editors define a per-podcast map of subtopic name
-- to importance weight (1-10). The Stage 1 LLM call classifies each article
-- into one of those subtopics (or null). The composer uses weights to allocate
-- script time and rolls low-weight subtopics into a labeled rapid-fire segment.

ALTER TABLE podcasts ADD COLUMN subtopics TEXT;
ALTER TABLE podcasts ADD COLUMN rapid_fire_weight_threshold INTEGER NOT NULL DEFAULT 3;

ALTER TABLE articles ADD COLUMN subtopic TEXT;
