## MODIFIED Requirements

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
