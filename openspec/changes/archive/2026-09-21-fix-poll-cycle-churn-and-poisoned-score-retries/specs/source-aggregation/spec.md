## MODIFIED Requirements

### Requirement: Time-windowed aggregation
The aggregator SHALL only include posts whose age falls within a configurable time window. Age SHALL be measured on `COALESCE(published_at, created_at)`: the date the feed claimed, falling back to the ingestion timestamp for posts a feed gave no date for. The time window SHALL default to the value of `app.source.max-article-age-days` (default 7 days). Posts outside the time window SHALL NOT be included in aggregation, even if they have no `post_articles` entry.

The window is measured on the same expression that the `content-store` capability uses to delete old unprocessed articles, so that aggregation only ever produces articles that retention will keep. A post whose `published_at` is outside the window would otherwise be aggregated into an article that the next cleanup pass deletes, unlinking the post again and re-queueing it for the next poll.

#### Scenario: Posts within time window included
- **WHEN** aggregating with a 7-day window and 3 posts were published in the last 5 days
- **THEN** all 3 posts are included in the aggregation

#### Scenario: Posts outside time window excluded
- **WHEN** aggregating with a 7-day window and a post was published 10 days ago
- **THEN** that post is excluded from aggregation

#### Scenario: Mixed-age posts partially included
- **WHEN** aggregating with a 7-day window and 5 posts exist, 3 published in the last 3 days and 2 published 10 days ago
- **THEN** only the 3 recent posts are included in the aggregation

#### Scenario: Back-dated post excluded despite recent ingestion
- **WHEN** aggregating with a 7-day window and a post was ingested today but the feed published it 30 days ago
- **THEN** that post is excluded from aggregation, and no article is created that the next cleanup pass would delete
