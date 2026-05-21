## Why

The current `SourceAggregator` bundles ALL posts from a Twitter/Nitter source into a single article per poll cycle. This creates two problems: (1) the aggregated article URL points to the RSS feed (e.g. `nitter.net/user/rss`) which is useless as a source link, and (2) the article title is a generic "Posts from @user" label instead of describing what the post is about. Individual tweet articles have the opposite problem: their title is the full tweet text which is too long for display. These issues make the sources HTML page less useful for listeners trying to find the content that backs a topic.

## What Changes

- `SourceAggregator` groups posts by thread (parent + replies) instead of bundling all posts into a single article. Each thread becomes its own article with the parent tweet URL and title
- Reply detection: posts whose title starts with "R to @" are identified as replies and attached to the nearest preceding parent post from the same source
- Nitter URLs are rewritten to x.com URLs for article URLs (e.g. `nitter.net/user/status/123` becomes `x.com/user/status/123`)
- `EpisodeSourcesGenerator` truncates article titles longer than 120 characters with "..." for display
- Backfill: regenerate articles for the last Agentic AI podcast episode to demonstrate the improvement

## Capabilities

### New Capabilities

_(none)_

### Modified Capabilities

- `source-aggregation`: Aggregation changes from all-posts-into-one to thread-based grouping with reply detection and nitter→x.com URL rewriting
- `episode-sources-file`: Article titles truncated at 120 characters for display

## Impact

- **`SourceAggregator`**: Major rework of `aggregatePosts()` method to detect threads and produce one article per thread
- **`EpisodeSourcesGenerator`**: Minor change to truncate long titles at display time
- **Existing data**: No migration needed. New articles will use thread-based aggregation. Backfill for last episode only
- **LLM pipeline**: Benefits from smaller, more focused articles (each thread scored independently instead of one giant blob)
- **Post-article links**: Each post still links to its article via `post_articles`, but now multiple articles per source per cycle instead of one
