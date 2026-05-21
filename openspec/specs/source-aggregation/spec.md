# Capability: Source Aggregation

## Purpose

Hybrid auto-detect + per-source override aggregation of short-form content items (tweets, microposts) into a single article per source per poll cycle for more effective LLM processing.
## Requirements
### Requirement: Source article aggregation
The system SHALL provide a `SourceAggregator` component that merges multiple posts from a single source into one consolidated digest article. The aggregator SHALL be invoked during script generation (in the LLM pipeline), NOT during source polling. When a source has 0 or 1 posts, the aggregator SHALL return them as individual articles (1:1 mapping). The aggregator SHALL create entries in the `post_articles` join table linking each post to its resulting article.

#### Scenario: Multiple posts aggregated into digest article
- **WHEN** a source with aggregation enabled has 5 unlinked posts within the time window
- **THEN** the aggregator merges them into a single article with all post texts joined by `\n\n---\n\n` separators, and creates 5 `post_articles` entries

#### Scenario: Single post returned as individual article
- **WHEN** a source with aggregation enabled has 1 unlinked post within the time window
- **THEN** the aggregator creates a 1:1 article from the post with 1 `post_articles` entry

#### Scenario: No posts returns empty list
- **WHEN** a source with aggregation enabled has 0 unlinked posts within the time window
- **THEN** the aggregator returns an empty list

#### Scenario: Non-aggregated source creates individual articles
- **WHEN** a source with aggregation disabled has 3 unlinked posts
- **THEN** the aggregator creates 3 individual articles, each linked to its source post via `post_articles`

### Requirement: Time-windowed aggregation
The aggregator SHALL only include posts whose `created_at` falls within a configurable time window. The time window SHALL default to the value of `app.source.max-article-age-days` (default 7 days). Posts outside the time window SHALL NOT be included in aggregation, even if they have no `post_articles` entry.

#### Scenario: Posts within time window included
- **WHEN** aggregating with a 7-day window and 3 posts were created in the last 5 days
- **THEN** all 3 posts are included in the aggregation

#### Scenario: Posts outside time window excluded
- **WHEN** aggregating with a 7-day window and a post was created 10 days ago
- **THEN** that post is excluded from aggregation

#### Scenario: Mixed-age posts partially included
- **WHEN** aggregating with a 7-day window and 5 posts exist — 3 from the last 3 days and 2 from 10 days ago
- **THEN** only the 3 recent posts are included in the aggregation

### Requirement: Aggregated article format
Each thread article SHALL have the following fields:
- `title`: The parent post's title (full text, not truncated)
- `body`: Parent post body followed by reply bodies joined with `\n\n---\n\n`, each prefixed with its `publishedAt` timestamp on a separate line if available
- `url`: The parent post's URL, with nitter.net hostname rewritten to x.com
- `publishedAt`: The parent post's `publishedAt` value
- `author`: The parent post's author value, or `null` if no author
- `contentHash`: Computed as SHA-256 of the combined body

#### Scenario: Thread article URL rewritten from nitter to x.com
- **WHEN** a thread's parent post has URL `https://nitter.net/user/status/12345#m`
- **THEN** the article URL is `https://x.com/user/status/12345#m`

#### Scenario: Thread article title is parent post title
- **WHEN** a thread has parent post with title "Gemini 3.1 Flash Live is now available..."
- **THEN** the article title is "Gemini 3.1 Flash Live is now available..."

#### Scenario: Thread article body includes replies
- **WHEN** a thread has parent body "Main content" and one reply body "Additional link"
- **THEN** the article body contains both texts separated by `\n\n---\n\n`

#### Scenario: Non-nitter URLs unchanged
- **WHEN** a thread's parent post has URL `https://example.com/post/123`
- **THEN** the article URL is `https://example.com/post/123` (unchanged)

### Requirement: Hybrid aggregation detection
The system SHALL determine whether to aggregate a source's posts using a hybrid approach:
1. If the source has `aggregate` = `true`, always aggregate
2. If the source has `aggregate` = `false`, never aggregate
3. If the source has `aggregate` = `null` (default), auto-detect:
   - Aggregate if source type is `"twitter"`
   - Aggregate if source URL contains `nitter.net`
   - Do not aggregate otherwise

#### Scenario: Explicit aggregate true overrides auto-detect
- **WHEN** an RSS source with URL `https://example.com/feed.xml` has `aggregate` = `true`
- **THEN** posts from this source are aggregated into a digest article

#### Scenario: Explicit aggregate false overrides auto-detect
- **WHEN** a source with type `"twitter"` has `aggregate` = `false`
- **THEN** posts from this source are NOT aggregated (each post becomes an individual article)

#### Scenario: Auto-detect aggregates twitter type
- **WHEN** a source with type `"twitter"` has `aggregate` = `null`
- **THEN** posts from this source are aggregated

#### Scenario: Auto-detect aggregates nitter URL
- **WHEN** an RSS source with URL `https://nitter.net/user/rss` has `aggregate` = `null`
- **THEN** posts from this source are aggregated

#### Scenario: Auto-detect does not aggregate regular RSS
- **WHEN** an RSS source with URL `https://example.com/feed.xml` has `aggregate` = `null`
- **THEN** posts from this source are NOT aggregated (each post becomes an individual article)

### Requirement: Thread detection
The aggregator SHALL detect threads by identifying reply posts and grouping them with their parent. A post SHALL be considered a reply if its title starts with "R to @" (case-sensitive). Posts SHALL be sorted by `publishedAt` ascending before grouping. Each non-reply post starts a new thread. Each reply post SHALL be attached to the most recent preceding non-reply post (the current parent). If a reply has no preceding parent, it SHALL be treated as a standalone thread.

#### Scenario: Reply grouped with parent
- **WHEN** posts are ["Parent post" at 17:00:00, "R to @user: reply" at 17:00:01]
- **THEN** both posts form one thread with "Parent post" as the parent

#### Scenario: Multiple replies grouped with parent
- **WHEN** posts are ["Parent" at 17:00:00, "R to @user: reply 1" at 17:00:01, "R to @user: reply 2" at 17:00:02]
- **THEN** all 3 posts form one thread

#### Scenario: Multiple threads detected
- **WHEN** posts are ["Thread A" at 10:00, "R to @user: A reply" at 10:01, "Thread B" at 15:00, "R to @user: B reply" at 15:01]
- **THEN** 2 threads are created: [Thread A + A reply] and [Thread B + B reply]

#### Scenario: Orphan reply becomes standalone thread
- **WHEN** the first post is "R to @user: orphan reply" with no preceding parent
- **THEN** it becomes a standalone thread with the reply as the parent

#### Scenario: Standalone posts become single-post threads
- **WHEN** posts are ["Standalone A" at 10:00, "Standalone B" at 15:00] and neither starts with "R to @"
- **THEN** 2 single-post threads are created

