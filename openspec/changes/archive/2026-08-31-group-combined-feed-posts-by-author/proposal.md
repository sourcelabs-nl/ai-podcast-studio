## Why

The X sources used to be one source per account, so "one source per poll cycle" and "one account" were the same thing and the aggregator's per-source threading gave a digest per account. They have since been replaced by a single Narro feed, `X via Narro`, that merges many accounts into one RSS document. The unit the aggregator reasons about no longer matches the unit the content comes in.

Today that source holds 476 posts and 476 articles, one per post. A snapshot of the feed shows 50 items from 8 distinct authors, very unevenly:

| handle | posts |
|---|---|
| ivanfioravanti | 30 |
| steipete | 9 |
| badlogicgames | 4 |
| rachpradhan, hwchase17 | 2 each |
| rauchg, nicolaygerold, amasad | 1 each |

So one account contributes 30 separate articles, mostly replies scoring 1-8, each taking a candidate slot of its own. That is a large part of why the age gate handed dedup 183 candidates on 31 August and the response overran its token budget.

Three things stop the existing aggregator from handling this feed:

- The source has `aggregate = false`, so every post becomes its own article.
- Setting it to `true` would change nothing. `groupPostsByThread` detects a reply by `title.startsWith("R to @")`, which was Nitter's convention. Narro marks a reply with a `narro-reply-header` span instead, so no post is ever seen as a reply and each one still becomes a single-post thread.
- `groupPostsByThread` is author-blind. It sorts every post in the source by time and attaches each reply to the most recent preceding post regardless of who wrote it, so on a combined feed it would splice different people's posts into one article.

The author is reliably recoverable: every item's link is `https://x.com/<handle>/status/<id>`, the title is prefixed `@handle:`, and there is a `<dc:creator>` display name. `Post` already carries an `author` column.

Separately, post titles carry raw markup into the pipeline. `RssFeedFetcher` runs the entry *body* through `Jsoup.parse(...).text()` but uses `entry.title` verbatim, and Narro's titles contain escaped HTML, so article titles read `@ivanfioravanti: <span class="narro-reply-header">@Rawtrutholog</span>…` and that markup reaches the scoring and dedup prompts.

## What Changes

- Aggregation groups by author first, then by thread within that author. `SourceAggregator` resolves an author key per post and threads each author's posts separately, so a combined feed behaves like the per-account sources it replaced. A single-account feed has one author group and is unaffected.
- The author key is resolved from the post URL's `x.com/<handle>/status/…` path, falling back to the title's `@handle:` prefix and then to `post.author`. Posts with no resolvable author fall into one group, which reproduces exactly today's behaviour for feeds that carry no author at all.
- Narro replies are detected. `NarroFeed.replyTarget` reads the `narro-reply-header` marker from the raw entry HTML, and `RssFeedFetcher` normalises such an entry's title to the existing `R to @<handle>: ` convention, so thread detection needs no new rules and both feed formats travel the same path.
- Entry titles are stripped of markup. `RssFeedFetcher` runs the title through the same Jsoup text extraction the body already gets.
- The `X via Narro` source is switched to `aggregate = true` so it takes the aggregating path.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `source-aggregation`: thread detection gains an author-grouping step ahead of it, and recognises Narro's reply marker via the normalised title.

## Impact

- Backend: `SourceAggregator` (author grouping, `resolveAuthorKey`); new `NarroFeed` in the source package; `RssFeedFetcher` (title markup stripping, reply-title normalisation).
- Tests: `SourceAggregatorTest` and `RssFeedFetcherTest` gain author-grouping, reply-detection and title-cleaning cases.
- Data: existing Narro articles are unchanged; the grouping applies to posts aggregated from here on.
- Config: the `X via Narro` source's `aggregate` flag is set to `true` via the API.
- No schema or frontend change.
