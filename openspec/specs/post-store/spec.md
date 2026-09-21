# Capability: Post Store

## Purpose

Persistence for individual content items: the posts table with per-source and cross-source deduplication, the join table linking posts to the articles they were aggregated into, and the queries that find posts not yet linked.

## Requirements

### Requirement: Post persistence with deduplication
The system SHALL persist individual content items in a `posts` SQLite table with columns: `id` (auto-generated INTEGER PRIMARY KEY), `source_id` (TEXT, NOT NULL, FK to sources), `title` (TEXT, NOT NULL), `body` (TEXT, NOT NULL), `url` (TEXT, NOT NULL), `published_at` (TEXT, nullable), `author` (TEXT, nullable), `content_hash` (TEXT, NOT NULL), `created_at` (TEXT, NOT NULL, ISO-8601 timestamp of when the post was stored). Deduplication SHALL be enforced via a unique constraint on `(source_id, content_hash)`. An index SHALL exist on `(source_id, created_at)` for efficient time-windowed queries. Additionally, the system SHALL provide a cross-source deduplication query to check whether a post with a given content hash already exists across a set of source IDs (all sources within the same podcast).

#### Scenario: New post stored successfully
- **WHEN** a post with a unique content hash for its source is saved
- **THEN** the post is persisted with all fields populated and `created_at` set to the current timestamp

#### Scenario: Duplicate post rejected
- **WHEN** a post with an already-existing `(source_id, content_hash)` combination is saved
- **THEN** the duplicate is silently ignored and the existing record remains unchanged

#### Scenario: Posts from different sources with same content hash
- **WHEN** two posts from different sources have the same content hash
- **THEN** both posts are stored (deduplication is scoped per source)

#### Scenario: Cross-source duplicate detected within same podcast
- **WHEN** source A and source B belong to the same podcast, source A already has a post with content hash "abc123", and source B polls a post with the same content hash "abc123"
- **THEN** the post from source B is silently skipped because a post with the same content hash already exists within the podcast's sources

#### Scenario: Same content hash across different podcasts is allowed
- **WHEN** source A belongs to podcast 1 and source B belongs to podcast 2, and both poll a post with content hash "abc123"
- **THEN** both posts are stored because cross-source dedup is scoped to sources within the same podcast

### Requirement: Post-article join table
The system SHALL maintain a `post_articles` join table with columns: `id` (auto-generated INTEGER PRIMARY KEY), `post_id` (INTEGER, NOT NULL, FK to posts), `article_id` (INTEGER, NOT NULL, FK to articles). A unique constraint SHALL exist on `(post_id, article_id)` to prevent duplicate linkage. This table enables traceability from articles back to their source posts and supports reprocessing by allowing the same post to be linked to multiple articles.

#### Scenario: Posts linked to article during aggregation
- **WHEN** 5 posts are aggregated into a single article
- **THEN** 5 rows are created in `post_articles`, each linking one post to the article

#### Scenario: Non-aggregated post linked to article
- **WHEN** a single post from a non-aggregated source is converted to an article
- **THEN** 1 row is created in `post_articles` linking the post to its article

#### Scenario: Same post linked to multiple articles
- **WHEN** a post is reprocessed and included in a new article
- **THEN** a second `post_articles` row is created linking the post to the new article, while the original link remains

#### Scenario: Duplicate linkage prevented
- **WHEN** a `(post_id, article_id)` combination already exists in `post_articles`
- **THEN** the duplicate insert is rejected

### Requirement: Unlinked post queries
The system SHALL provide a repository method to find posts that have no entry in `post_articles` for a given set of source IDs and within a time window. This query supports finding posts that have not yet been aggregated into any article.

Post age SHALL be measured on `COALESCE(published_at, created_at)`: the date the feed claimed, falling back to the ingestion timestamp for posts a feed gave no date for. This matches how the `content-store` capability measures article age when deleting old unprocessed articles. Measuring on `created_at` alone leaves a post whose `published_at` is outside the retention window permanently eligible for aggregation while the article it produces is permanently ineligible to survive, so aggregation, article cleanup, and `ON DELETE CASCADE` on `post_articles` form a loop that re-aggregates and re-scores the same post on every poll.

#### Scenario: Find unlinked posts within time window
- **WHEN** querying for unlinked posts for source "src-1" within the last 7 days
- **THEN** only posts from source "src-1" published within the last 7 days that have no entry in `post_articles` are returned

#### Scenario: Previously linked posts excluded
- **WHEN** a post has been linked to an article via `post_articles`
- **THEN** that post is NOT returned by the unlinked posts query

#### Scenario: Posts outside time window excluded
- **WHEN** querying with a 7-day time window and a post was published 10 days ago
- **THEN** that post is NOT returned even if it has no `post_articles` entry

#### Scenario: Back-dated post excluded despite recent ingestion
- **WHEN** querying with a 7-day time window and a post was ingested today but the feed published it 30 days ago
- **THEN** that post is NOT returned, because any article aggregated from it would be deleted by the next article cleanup pass

#### Scenario: Recently published post included despite old ingestion
- **WHEN** querying with a 7-day time window and a post was ingested 30 days ago but published yesterday
- **THEN** that post is returned

#### Scenario: Dateless post falls back to its ingestion timestamp
- **WHEN** querying with a 7-day time window and an unlinked post has a null `published_at` and was ingested 2 days ago
- **THEN** that post is returned

### Requirement: Old unprocessed post cleanup
The system SHALL periodically delete posts that are older than the configured maximum article age (`app.source.max-article-age-days`) AND have no entry in `post_articles`. Posts that have been linked to at least one article SHALL NOT be deleted regardless of age. Post age SHALL be measured on `COALESCE(published_at, created_at)`, the same expression the unlinked-post queries use, so that a post too old to be aggregated is also reclaimable by cleanup rather than retained indefinitely.

#### Scenario: Old unlinked posts deleted
- **WHEN** cleanup runs and posts exist published earlier than `max-article-age-days` ago that have no `post_articles` entries
- **THEN** those posts are deleted

#### Scenario: Old linked posts retained
- **WHEN** cleanup runs and posts exist published earlier than `max-article-age-days` ago that have `post_articles` entries
- **THEN** those posts are NOT deleted (they are historical records linked to articles)

#### Scenario: Recent unlinked posts retained
- **WHEN** cleanup runs and posts exist published within `max-article-age-days` that have no `post_articles` entries
- **THEN** those posts are NOT deleted (they may be aggregated in a future pipeline run)

#### Scenario: Back-dated unlinked post deleted despite recent ingestion
- **WHEN** cleanup runs and an unlinked post was ingested today but the feed published it 30 days ago
- **THEN** that post is deleted, rather than being retained for an aggregation that could never produce a surviving article

#### Scenario: Recently published unlinked post retained despite old ingestion
- **WHEN** cleanup runs and an unlinked post was ingested 30 days ago but published yesterday
- **THEN** that post is NOT deleted
