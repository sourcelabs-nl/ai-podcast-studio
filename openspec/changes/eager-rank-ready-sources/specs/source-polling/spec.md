## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: Post-round eager ranking trigger
After the poll `supervisorScope` in `SourcePollingScheduler.pollSources()` completes and before recording `lastPollRoundCompletedAt`, the scheduler SHALL trigger eager ranking for each distinct podcast that had a source due in that round, via a `PodcastService` delegate (per the scheduler authoring rules: the scheduler triggers, the service owns the logic). The trigger SHALL run sequentially and inline so that two consecutive poll cycles cannot concurrently score the same unscored article. A failure ranking one podcast SHALL be logged and SHALL NOT abort ranking of the other podcasts or the poll loop.

#### Scenario: Eager ranking triggered for polled podcasts
- **WHEN** a poll round finishes and two distinct podcasts had due sources
- **THEN** the scheduler invokes eager ranking once for each of those two podcasts before recording the round completion time

#### Scenario: Eager ranking failure is isolated
- **WHEN** eager ranking throws for one podcast during the post-round step
- **THEN** the error is logged and ranking continues for the remaining podcasts and the poll loop is unaffected
