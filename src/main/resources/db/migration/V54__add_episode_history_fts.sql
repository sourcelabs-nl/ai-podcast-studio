-- FTS5 virtual table indexing GENERATED episodes' recap, script_text, and the joined
-- list of episode_articles.topic, scoped per podcast. Powers the searchPastEpisodes
-- Spring AI tool used during compose to detect prior coverage of a topic.

CREATE VIRTUAL TABLE episode_history_fts USING fts5(
    episode_id UNINDEXED,
    podcast_id UNINDEXED,
    generated_at UNINDEXED,
    topics,
    recap,
    script_text,
    tokenize = 'porter unicode61'
);

-- Backfill from existing GENERATED episodes.
INSERT INTO episode_history_fts (episode_id, podcast_id, generated_at, topics, recap, script_text)
SELECT
    e.id,
    e.podcast_id,
    e.generated_at,
    COALESCE(
        (SELECT GROUP_CONCAT(ea.topic, ', ')
         FROM episode_articles ea
         WHERE ea.episode_id = e.id AND ea.topic IS NOT NULL),
        ''
    ),
    COALESCE(e.recap, ''),
    COALESCE(e.script_text, '')
FROM episodes e
WHERE e.status = 'GENERATED';

-- Episode INSERT: index immediately when created as GENERATED.
CREATE TRIGGER episode_history_fts_ai
AFTER INSERT ON episodes
WHEN NEW.status = 'GENERATED'
BEGIN
    INSERT INTO episode_history_fts (episode_id, podcast_id, generated_at, topics, recap, script_text)
    VALUES (
        NEW.id,
        NEW.podcast_id,
        NEW.generated_at,
        COALESCE(
            (SELECT GROUP_CONCAT(ea.topic, ', ')
             FROM episode_articles ea
             WHERE ea.episode_id = NEW.id AND ea.topic IS NOT NULL),
            ''
        ),
        COALESCE(NEW.recap, ''),
        COALESCE(NEW.script_text, '')
    );
END;

-- Status transitions into GENERATED: insert/replace the FTS row.
CREATE TRIGGER episode_history_fts_au_status_in
AFTER UPDATE OF status ON episodes
WHEN NEW.status = 'GENERATED' AND OLD.status != 'GENERATED'
BEGIN
    DELETE FROM episode_history_fts WHERE episode_id = NEW.id;
    INSERT INTO episode_history_fts (episode_id, podcast_id, generated_at, topics, recap, script_text)
    VALUES (
        NEW.id,
        NEW.podcast_id,
        NEW.generated_at,
        COALESCE(
            (SELECT GROUP_CONCAT(ea.topic, ', ')
             FROM episode_articles ea
             WHERE ea.episode_id = NEW.id AND ea.topic IS NOT NULL),
            ''
        ),
        COALESCE(NEW.recap, ''),
        COALESCE(NEW.script_text, '')
    );
END;

-- Status transitions out of GENERATED: remove the FTS row.
CREATE TRIGGER episode_history_fts_au_status_out
AFTER UPDATE OF status ON episodes
WHEN OLD.status = 'GENERATED' AND NEW.status != 'GENERATED'
BEGIN
    DELETE FROM episode_history_fts WHERE episode_id = NEW.id;
END;

-- Recap update on a GENERATED episode: refresh the FTS row.
CREATE TRIGGER episode_history_fts_au_recap
AFTER UPDATE OF recap ON episodes
WHEN NEW.status = 'GENERATED'
BEGIN
    DELETE FROM episode_history_fts WHERE episode_id = NEW.id;
    INSERT INTO episode_history_fts (episode_id, podcast_id, generated_at, topics, recap, script_text)
    VALUES (
        NEW.id,
        NEW.podcast_id,
        NEW.generated_at,
        COALESCE(
            (SELECT GROUP_CONCAT(ea.topic, ', ')
             FROM episode_articles ea
             WHERE ea.episode_id = NEW.id AND ea.topic IS NOT NULL),
            ''
        ),
        COALESCE(NEW.recap, ''),
        COALESCE(NEW.script_text, '')
    );
END;

-- Script update on a GENERATED episode: refresh the FTS row.
CREATE TRIGGER episode_history_fts_au_script
AFTER UPDATE OF script_text ON episodes
WHEN NEW.status = 'GENERATED'
BEGIN
    DELETE FROM episode_history_fts WHERE episode_id = NEW.id;
    INSERT INTO episode_history_fts (episode_id, podcast_id, generated_at, topics, recap, script_text)
    VALUES (
        NEW.id,
        NEW.podcast_id,
        NEW.generated_at,
        COALESCE(
            (SELECT GROUP_CONCAT(ea.topic, ', ')
             FROM episode_articles ea
             WHERE ea.episode_id = NEW.id AND ea.topic IS NOT NULL),
            ''
        ),
        COALESCE(NEW.recap, ''),
        COALESCE(NEW.script_text, '')
    );
END;

-- Episode deletion: cleanup.
CREATE TRIGGER episode_history_fts_ad
AFTER DELETE ON episodes
BEGIN
    DELETE FROM episode_history_fts WHERE episode_id = OLD.id;
END;

-- episode_articles changes recompute the joined topics column for the affected episode.
CREATE TRIGGER episode_history_fts_ea_ai
AFTER INSERT ON episode_articles
BEGIN
    UPDATE episode_history_fts
    SET topics = COALESCE(
        (SELECT GROUP_CONCAT(ea.topic, ', ')
         FROM episode_articles ea
         WHERE ea.episode_id = NEW.episode_id AND ea.topic IS NOT NULL),
        ''
    )
    WHERE episode_id = NEW.episode_id;
END;

CREATE TRIGGER episode_history_fts_ea_au
AFTER UPDATE OF topic ON episode_articles
BEGIN
    UPDATE episode_history_fts
    SET topics = COALESCE(
        (SELECT GROUP_CONCAT(ea.topic, ', ')
         FROM episode_articles ea
         WHERE ea.episode_id = NEW.episode_id AND ea.topic IS NOT NULL),
        ''
    )
    WHERE episode_id = NEW.episode_id;
END;

CREATE TRIGGER episode_history_fts_ea_ad
AFTER DELETE ON episode_articles
BEGIN
    UPDATE episode_history_fts
    SET topics = COALESCE(
        (SELECT GROUP_CONCAT(ea.topic, ', ')
         FROM episode_articles ea
         WHERE ea.episode_id = OLD.episode_id AND ea.topic IS NOT NULL),
        ''
    )
    WHERE episode_id = OLD.episode_id;
END;
