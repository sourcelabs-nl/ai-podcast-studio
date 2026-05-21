## MODIFIED Requirements

### Requirement: Source article aggregation
The system SHALL provide a `SourceAggregator` component that merges multiple posts from a single source into consolidated articles. When aggregation is enabled for a source, posts SHALL be grouped by thread: each parent post and its replies form one thread, and each thread produces one article. When a source has 0 or 1 posts, the aggregator SHALL return them as individual articles (1:1 mapping). The aggregator SHALL create entries in the `post_articles` join table linking each post to its resulting article.

#### Scenario: Multiple posts grouped into thread-based articles
- **WHEN** a source with aggregation enabled has 8 posts forming 3 threads (2 parents with 2 replies each, 2 standalone posts)
- **THEN** the aggregator creates 4 articles (one per thread/standalone), each with its posts linked via `post_articles`

#### Scenario: Single post returned as individual article
- **WHEN** a source with aggregation enabled has 1 unlinked post within the time window
- **THEN** the aggregator creates a 1:1 article from the post with 1 `post_articles` entry

#### Scenario: No posts returns empty list
- **WHEN** a source with aggregation enabled has 0 unlinked posts within the time window
- **THEN** the aggregator returns an empty list

#### Scenario: Non-aggregated source creates individual articles
- **WHEN** a source with aggregation disabled has 3 unlinked posts
- **THEN** the aggregator creates 3 individual articles, each linked to its source post via `post_articles`

## ADDED Requirements

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

## MODIFIED Requirements

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
