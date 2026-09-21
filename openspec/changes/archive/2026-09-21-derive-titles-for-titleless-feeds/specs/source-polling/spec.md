## MODIFIED Requirements

### Requirement: RSS/Atom feed polling
The system SHALL parse RSS and Atom feeds using ROME (`com.rometools:rome`). For sources with type `rss`, the system SHALL fetch the feed, extract entries published after the source's `last_seen_id` timestamp, and store each new entry as a post in the `posts` table. The system SHALL strip HTML markup from the entry content and description using `Jsoup.parse(value).text()` before storing the post body.

The system SHALL resolve each entry's title as follows: use `SyndEntry.title` when it is non-blank; otherwise derive a title from the entry's plain-text body. A derived title SHALL be the body's first sentence when a sentence break falls within the first 100 characters, and otherwise the body truncated on a word boundary within 100 characters and ellipsized. An entry SHALL be dropped only when it has neither a usable title nor a body, since feeds that omit `<title>` entirely (Mastodon and the X-mirror instances built on it) would otherwise yield no posts at all.

The system SHALL extract the author from the RSS entry: use `SyndEntry.author` if non-blank, otherwise use the `name` of the first entry in `SyndEntry.authors` if available. If neither provides a non-blank value, `post.author` SHALL be null. The `SourceAggregator` SHALL NOT be called inside `SourcePoller.poll`. Aggregation of non-aggregate sources MAY occur in the post-round eager-ranking step (see the `eager-source-ranking` capability); aggregation of aggregate sources (Twitter/nitter) remains deferred to script generation time.

#### Scenario: New RSS entries stored as individual posts
- **WHEN** an RSS feed contains 3 entries published after the last-seen timestamp
- **THEN** 3 new posts are created in the `posts` table with title, clean plain-text body, URL, published timestamp, author (when available), and content hash

#### Scenario: Nitter RSS entries stored as individual posts
- **WHEN** a nitter RSS feed contains 5 entries published after the last-seen timestamp
- **THEN** 5 individual posts are stored in the `posts` table (aggregation happens later at script generation time)

#### Scenario: Titleless entry gets a title derived from its body
- **WHEN** an entry has no `<title>` element and a body of "We worked with METR and Redwood Research to conduct a third-party assessment."
- **THEN** the stored post's title is that sentence, and the post is stored rather than dropped

#### Scenario: Long titleless body truncated on a word boundary
- **WHEN** an entry has no `<title>` and a body longer than 100 characters with no sentence break in that span
- **THEN** the stored title is the body truncated at a word boundary within 100 characters, ending in an ellipsis

#### Scenario: Explicit title preferred over the body
- **WHEN** an entry has a non-blank `<title>` and a body
- **THEN** the stored post's title is the entry's own title

#### Scenario: Entry with neither title nor body is dropped
- **WHEN** an entry has no `<title>` and no content or description
- **THEN** no post is created for that entry

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
