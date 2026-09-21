## MODIFIED Requirements

### Requirement: Skip non-article hosts and source types
The RSS fetcher SHALL NOT deep-fetch links whose host matches any configured skip-host substring (default: `x.com`, `twitter.com`, `nitter`, `youtube.com`, `youtu.be`), nor when the link is unparseable. YouTube sources, which are polled through the RSS fetcher, SHALL be polled with deep-fetch disabled. Source URL validation SHALL also invoke the fetcher with deep-fetch disabled, since validation only needs the feed to parse and yield an item.

#### Scenario: Configured host is skipped
- **WHEN** an RSS entry links to a host containing a configured skip term (e.g. `x.com`)
- **THEN** the fetcher keeps the feed body and does not attempt a deep-fetch

#### Scenario: YouTube source polled without deep-fetch
- **WHEN** a source of type YOUTUBE is polled
- **THEN** the RSS fetcher is invoked with deep-fetch disabled

#### Scenario: Source validation does not deep-fetch
- **WHEN** a new RSS source is created and its URL is validated
- **THEN** the fetcher is invoked with deep-fetch disabled, and no linked article is retrieved

## ADDED Requirements

### Requirement: Entries outside the poll's time bounds are excluded before deep-fetch
Because the RSS fetcher deep-fetches every entry it returns, the bounds that decide which entries are worth keeping SHALL be applied before deep-fetch rather than only when saving. For an RSS source, the system SHALL compute the earliest publish time worth fetching as the latest of: the source's `last_seen_id`, the max-article-age cutoff (`app.source.max-article-age-days`, or the per-poll override), and, when the source has never been polled, the source's creation time. The system SHALL pass that instant to the fetcher as the lower bound on entry publish time.

The same bounds SHALL continue to be enforced when saving posts, which covers the source types that do not pass through the RSS entry filter.

#### Scenario: Large archive is not crawled on first poll
- **WHEN** a source is added for a feed carrying an archive of over a thousand entries
- **THEN** the fetcher receives the source's creation time as the lower bound, returns no entries, and deep-fetches nothing

#### Scenario: Age cutoff reaches the fetcher
- **WHEN** an RSS source with no `last_seen_id` is polled with a max article age of 7 days
- **THEN** the fetcher is invoked with a lower bound 7 days in the past, so older entries are never deep-fetched

#### Scenario: Last-seen timestamp wins when it is the tightest bound
- **WHEN** a polled source's `last_seen_id` is more recent than the max-article-age cutoff
- **THEN** the fetcher is invoked with the `last_seen_id` instant

#### Scenario: Bounds still applied when saving
- **WHEN** a TWITTER or WEBSITE source returns a post older than the max-article-age cutoff
- **THEN** the post is not saved, as before
