-- Undated feed items take the moment we first saw them as their publication date, the latest they
-- can have been published. Before this, an undated article was admitted to every episode window:
-- episode 297 (window 16-19 October) carried Google Developers Blog posts first seen on 24 September.
--
-- Posts take their own created_at. An article takes the earliest created_at of its posts; one with
-- no linked post keeps a null date and is still kept by the eligibility filter.
UPDATE posts SET published_at = created_at WHERE published_at IS NULL;

UPDATE articles
SET published_at = (
    SELECT MIN(p.created_at)
    FROM post_articles pa
    JOIN posts p ON p.id = pa.post_id
    WHERE pa.article_id = articles.id
)
WHERE published_at IS NULL;

-- The poller now looks up a source's posts by URL to skip an item re-served with edited content.
CREATE INDEX IF NOT EXISTS idx_posts_source_url ON posts(source_id, url);
