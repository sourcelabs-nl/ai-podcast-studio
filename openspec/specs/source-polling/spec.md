# Capability: Source Polling

## Purpose

Scheduled polling of configured content sources (RSS feeds and websites), extracting articles and deduplicating via content hashing.
## Requirements
### Requirement: Scheduled source polling
The system SHALL poll each enabled source on a configurable interval. A `SourcePollingScheduler` SHALL launch a coroutine loop on `ApplicationReadyEvent` that runs every 60 seconds, iterates over all enabled sources, and polls each source whose effective poll interval has elapsed since its last poll. The coroutine loop SHALL use its own `CoroutineScope(Dispatchers.Default + SupervisorJob())` so it runs independently from other schedulers and cannot be starved by them. The scope SHALL be cancelled on `@PreDestroy` for graceful shutdown. The effective poll interval SHALL account for exponential backoff: for sources with `consecutiveFailures > 0`, the interval is `pollIntervalMinutes × 2^consecutiveFailures`, capped at `app.source.max-backoff-hours` converted to minutes. For sources with `consecutiveFailures = 0`, the normal `pollIntervalMinutes` is used. For source types that require per-user API keys (e.g., `"twitter"`), the scheduler SHALL resolve the podcast's owner user ID and pass it to the `SourcePoller`.

The scheduler's `pollSources()` method SHALL be a `suspend fun`. Due sources SHALL be grouped by URL host (extracted via `java.net.URI(url).host`). Each host group SHALL be polled as a parallel coroutine under `supervisorScope`, with sequential polling and configurable delays within each group (as defined by the `poll-rate-limiting` capability).

Sources with `lastPolled = null` (never polled) SHALL receive startup jitter before being checked for due status (as defined by the `poll-rate-limiting` capability).

All `[Polling]` log messages in `SourcePoller` that identify a source SHALL use `source.url` instead of `source.id` so that operators can identify sources without a database lookup.

#### Scenario: Source polled when interval has elapsed
- **WHEN** the scheduler runs and a source's effective poll interval has elapsed since its `last_polled` timestamp
- **THEN** the source is polled for new content

#### Scenario: Source skipped when interval has not elapsed
- **WHEN** the scheduler runs and a source was polled less than its effective poll interval ago
- **THEN** the source is skipped in this polling cycle

#### Scenario: Disabled source never polled
- **WHEN** the scheduler runs and a source has `enabled: false`
- **THEN** the source is not polled

#### Scenario: Twitter source polled with user context
- **WHEN** the scheduler runs and a source with `type: "twitter"` is due for polling
- **THEN** the scheduler resolves the podcast owner's user ID and passes it to the poller so the fetcher can look up the user's X API key

#### Scenario: Source with failures uses backoff interval
- **WHEN** the scheduler runs and a source has `consecutiveFailures = 2` and `pollIntervalMinutes = 60`
- **THEN** the source is only polled if at least 240 minutes (60 × 2²) have elapsed since `lastPolled`

#### Scenario: Host groups polled in parallel
- **WHEN** the scheduler runs and due sources span multiple hosts
- **THEN** each host group is polled concurrently as a separate coroutine under `supervisorScope`

#### Scenario: Scheduler runs as independent coroutine loop
- **WHEN** the application starts
- **THEN** `SourcePollingScheduler` launches a coroutine loop that calls `pollSources()` every 60 seconds, independent of other schedulers

#### Scenario: Log messages show source URL
- **WHEN** a source is polled and log messages are emitted
- **THEN** the log messages identify the source by its URL (not its UUID)

### Requirement: RSS/Atom feed polling
The system SHALL parse RSS and Atom feeds using ROME (`com.rometools:rome`). For sources with type `rss`, the system SHALL fetch the feed, extract entries published after the source's `last_seen_id` timestamp, and store each new entry as a post in the `posts` table. The system SHALL strip HTML markup from the entry content and description using `Jsoup.parse(value).text()` before storing the post body. The system SHALL extract the author from the RSS entry: use `SyndEntry.author` if non-blank, otherwise use the `name` of the first entry in `SyndEntry.authors` if available. If neither provides a non-blank value, `post.author` SHALL be null. The `SourceAggregator` SHALL NOT be called inside `SourcePoller.poll`. Aggregation of non-aggregate sources MAY occur in the post-round eager-ranking step (see the `eager-source-ranking` capability); aggregation of aggregate sources (Twitter/nitter) remains deferred to script generation time.

#### Scenario: New RSS entries stored as individual posts
- **WHEN** an RSS feed contains 3 entries published after the last-seen timestamp
- **THEN** 3 new posts are created in the `posts` table with title, clean plain-text body, URL, published timestamp, author (when available), and content hash

#### Scenario: Nitter RSS entries stored as individual posts
- **WHEN** a nitter RSS feed contains 5 entries published after the last-seen timestamp
- **THEN** 5 individual posts are stored in the `posts` table (aggregation happens later at script generation time)

#### Scenario: No new entries since last poll
- **WHEN** an RSS feed contains no entries published after the last-seen timestamp
- **THEN** no new posts are created and the source's `last_polled` is still updated

#### Scenario: Feed fetch fails
- **WHEN** an RSS feed URL returns an error or is unreachable
- **THEN** the error is logged, no posts are created, and the source's `last_polled` is updated to avoid retrying immediately

#### Scenario: HTML content stripped from RSS entry
- **WHEN** an RSS entry contains body text with HTML tags like `<p>Breaking news</p><a href="...">link</a>`
- **THEN** the stored post body contains only "Breaking news link" (plain text, HTML stripped)

#### Scenario: Author extracted from RSS entry
- **WHEN** an RSS entry has `author` set to "John Smith"
- **THEN** the stored post has `author` = "John Smith"

### Requirement: Website scraping
The system SHALL scrape websites using Jsoup for sources with type `website`. The system SHALL fetch the page HTML, extract article content using a heuristic approach (select `<article>` tag or largest text-containing block), and store new content as a post in the `posts` table, identified by content hash comparison. The system SHALL extract the author from the HTML document by checking (in order): `<meta name="author" content="...">`, `<meta property="article:author" content="...">`. The first non-blank match SHALL be used as `post.author`. If no author meta tag is found, `post.author` SHALL be null.

#### Scenario: New content detected on website
- **WHEN** a website page contains content with a hash not present in the `posts` table for that source
- **THEN** a new post is created with the extracted text and author (when available)

#### Scenario: No content change on website
- **WHEN** a website page content hash matches an existing post's hash for that source
- **THEN** no new post is created

### Requirement: Resilient datetime parsing
The `SourcePoller` SHALL parse datetime strings resiliently, accepting both ISO-8601 format (`2026-02-19T10:48:10Z`) and SQLite datetime format (`2026-02-19 10:48:10`). A private `parseInstant()` method SHALL first attempt `Instant.parse()` (ISO-8601), and if that throws `DateTimeParseException`, SHALL fall back to parsing the string as a `LocalDateTime` (replacing the space separator with `T`) and converting to `Instant` assuming UTC. This ensures compatibility with timestamps stored by SQLite's `datetime()` function.

#### Scenario: ISO-8601 datetime parsed
- **WHEN** a source has `createdAt` in ISO-8601 format (e.g., `2026-02-19T10:48:10Z`)
- **THEN** it is parsed correctly as an `Instant`

#### Scenario: SQLite datetime format parsed
- **WHEN** a source has `createdAt` in SQLite format (e.g., `2026-02-19 10:48:10`)
- **THEN** it is parsed as UTC `Instant` by replacing the space with `T` and treating it as `LocalDateTime` at UTC

### Requirement: Content hash deduplication
The system SHALL compute a SHA-256 hash of each post's body text before storing. Posts with a `(source_id, content_hash)` combination already present in the `posts` table SHALL be silently skipped. Posts whose `publishedAt` is older than the configured maximum article age (`app.source.max-article-age-days`, default 7 days) SHALL be silently skipped. Posts with `publishedAt` = null SHALL NOT be filtered by age. Additionally, for a source's first poll (`lastPolled` is null), posts with `publishedAt` before the source's `createdAt` timestamp SHALL be silently skipped. Posts with `publishedAt` = null SHALL NOT be filtered by the source's `createdAt`.

#### Scenario: Identical content from different polls
- **WHEN** two separate polls produce posts with the same body text from the same source
- **THEN** only the first post is stored; the duplicate is silently skipped

#### Scenario: Post older than max age is skipped
- **WHEN** a post has `publishedAt` older than the configured `max-article-age-days`
- **THEN** the post is not saved to the `posts` table

#### Scenario: Post with null publishedAt is saved
- **WHEN** a post has `publishedAt` = null
- **THEN** the post is saved (age cannot be determined)

#### Scenario: First poll of new source skips historical posts
- **WHEN** a source with `lastPolled = null` and `createdAt = 2026-02-17T10:00:00Z` is polled and returns a post with `publishedAt = 2026-02-15T08:00:00Z`
- **THEN** the post is silently skipped because it was published before the source was created

#### Scenario: First poll of new source accepts recent posts
- **WHEN** a source with `lastPolled = null` and `createdAt = 2026-02-17T10:00:00Z` is polled and returns a post with `publishedAt = 2026-02-17T12:00:00Z`
- **THEN** the post is saved because it was published after the source was created

#### Scenario: Subsequent polls ignore createdAt filter
- **WHEN** a source with `lastPolled = 2026-02-17T12:00:00Z` is polled and returns a post with `publishedAt = 2026-02-16T08:00:00Z`
- **THEN** the `createdAt` filter is NOT applied (only applies on first poll); the post is subject to normal max-article-age filtering only

#### Scenario: First poll with null publishedAt post is not filtered
- **WHEN** a source with `lastPolled = null` is polled and returns a post with `publishedAt = null`
- **THEN** the post is saved (createdAt filter cannot be applied without a publish date)

### Requirement: Old unprocessed article cleanup
The system SHALL periodically delete unprocessed articles whose `publishedAt` is older than the configured maximum article age. The cleanup SHALL run as part of the source polling schedule, before sources are polled. Only articles with `is_processed` = false SHALL be deleted — processed articles are retained as historical records. Additionally, old unlinked posts SHALL be cleaned up on the same schedule (as defined in the `post-store` capability).

#### Scenario: Old unprocessed articles deleted
- **WHEN** the source polling scheduler runs and unprocessed articles exist with `publishedAt` older than `max-article-age-days`
- **THEN** those articles are deleted from the content store

#### Scenario: Old processed articles retained
- **WHEN** the source polling scheduler runs and processed articles exist with `publishedAt` older than `max-article-age-days`
- **THEN** those articles are not deleted (they are historical records of past episodes)

### Requirement: Poll round completion tracking
The system SHALL record the wall-clock time at which each successful poll round completes, kept in memory on the polling scheduler. Before any round has completed in the current process (for example immediately after startup), the recorded time SHALL be absent. This timestamp lets other components detect whether polling has been running recently (versus the process having been asleep or offline).

#### Scenario: Timestamp set after a poll round
- **WHEN** a poll round completes
- **THEN** the last-poll-round-completed time is set to the current time

#### Scenario: Timestamp absent before the first round
- **WHEN** the process has started but no poll round has completed yet
- **THEN** the last-poll-round-completed time is absent

### Requirement: On-demand poll of a single podcast's sources
The system SHALL support polling all enabled sources of a single podcast on demand, to completion, reusing the same host-grouped polling and per-host delay behavior as the scheduled poll loop. A blocking entry point SHALL be available for callers that are not themselves coroutines.

#### Scenario: Poll a podcast's sources on demand
- **WHEN** an on-demand poll is requested for a podcast
- **THEN** only that podcast's enabled sources are polled, and the call returns after they have all been polled

#### Scenario: Podcast with no enabled sources
- **WHEN** an on-demand poll is requested for a podcast that has no enabled sources
- **THEN** the call returns without polling anything

### Requirement: Post-round eager ranking trigger
After the poll `supervisorScope` in `SourcePollingScheduler.pollSources()` completes and before recording `lastPollRoundCompletedAt`, the scheduler SHALL trigger eager ranking for each distinct podcast that had a source due in that round, via a `PodcastService` delegate (per the scheduler authoring rules: the scheduler triggers, the service owns the logic). The trigger SHALL run sequentially and inline so that two consecutive poll cycles cannot concurrently score the same unscored article. A failure ranking one podcast SHALL be logged and SHALL NOT abort ranking of the other podcasts or the poll loop.

#### Scenario: Eager ranking triggered for polled podcasts
- **WHEN** a poll round finishes and two distinct podcasts had due sources
- **THEN** the scheduler invokes eager ranking once for each of those two podcasts before recording the round completion time

#### Scenario: Eager ranking failure is isolated
- **WHEN** eager ranking throws for one podcast during the post-round step
- **THEN** the error is logged and ranking continues for the remaining podcasts and the poll loop is unaffected

