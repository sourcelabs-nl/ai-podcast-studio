# Capability: Source Aggregation

## Purpose

Hybrid auto-detect + per-source override aggregation of short-form content items (tweets, microposts) into one article per author thread for more effective LLM processing. A source may be a combined feed carrying many accounts, so posts are grouped by author before their reply chains are threaded.

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
The aggregator SHALL group an aggregating source's posts by author BEFORE detecting threads, and SHALL detect threads independently within each author's posts.

The author key SHALL be resolved from the first of these that yields a value:
1. The `<handle>` path segment of a post URL of the form `x.com/<handle>/status/<id>` (or `twitter.com`), lowercased
2. A leading `@handle:` prefix in the post title, lowercased
3. The post's `author` field, trimmed, stripped of a leading `@`, and lowercased

The leading `@` is stripped because feeds write the author field both as `@simonw` and as `simonw`, and the key is compared against a reply target that never carries one.

Posts with no resolvable author key SHALL form a single group together, which reproduces the previous behaviour for feeds carrying no author information.

Author grouping is required because a source is no longer the same thing as an account. A combined feed (such as the Narro feed that replaced the per-account X sources) merges many accounts into one RSS document, and without grouping the reply-attachment rule below splices posts by different people into one article.

Within each author's posts, a post SHALL be considered a reply if its title starts with "R to @" (case-sensitive), and the account it answers SHALL be read from the `R to @<target>:` prefix. Posts SHALL be sorted by `publishedAt` ascending before grouping. Each non-reply post starts a new thread. A reply SHALL be attached to the most recent preceding thread by that same author ONLY when `<target>` matches the group's author key, compared case-insensitively; otherwise it SHALL start a new thread. A reply with no preceding thread within its author's posts SHALL be treated as a standalone thread. Where the group has no resolvable author key, or the target cannot be read from the title, a reply SHALL be attached as before, since there is nothing to compare against.

The target is the only signal the feeds carry. There is no conversation or thread identifier in a Narro document, and its reply marker names the account replied to rather than the tweet, so a reply to another account is the sole evidence that the author started a different conversation. In a 50-item sample, 28 of 29 replies answered their own author, so the rule leaves nearly all existing grouping untouched.

Feeds that mark replies with their own markup rather than an `R to @` title SHALL have that title normalised at ingestion (see the RSS ingestion requirement), so thread detection needs only the one convention.

#### Scenario: Posts grouped by author before threading
- **WHEN** an aggregating source's posts are ["@a: Parent" at 10:00 from x.com/a/status/1, "@b: Parent" at 10:01 from x.com/b/status/2, "R to @a: reply" at 10:02 from x.com/a/status/3]
- **THEN** the reply joins author a's thread, and author b's post remains its own thread

#### Scenario: A reply never attaches across authors
- **WHEN** author b's post is the most recent preceding post in time, but the reply's URL handle is author a
- **THEN** the reply is handled within author a's posts and never joins author b's post

#### Scenario: A reply to another account starts its own thread
- **WHEN** author a posted "@a: Parent" at 10:00 and then "R to @someone: text" at 10:01
- **THEN** two threads result, and the parent's article does not contain the reply

#### Scenario: A reply whose target cannot be read attaches as before
- **WHEN** a reply's title starts with "R to @" but carries no readable handle
- **THEN** it is attached to the author's preceding thread

#### Scenario: A self-reply continues the thread whatever the case of the handle
- **WHEN** author a posted "@a: Parent" at 10:00 and then "R to @A: text" at 10:01
- **THEN** one thread results containing both posts

#### Scenario: Author resolved from the post URL
- **WHEN** a post's URL is `https://x.com/ivanfioravanti/status/2094481641963938134`
- **THEN** its author key is `ivanfioravanti`

#### Scenario: Author resolved from the title prefix when the URL carries none
- **WHEN** a post's URL is `https://narro.info/item/abc` and its title is `@steipete: Some post text`
- **THEN** its author key is `steipete`

#### Scenario: Author resolved from the author field as a last resort
- **WHEN** a post has no x.com URL and no title handle prefix, and its author is `Ivan Fioravanti`
- **THEN** its author key is `ivan fioravanti`

#### Scenario: Posts with no resolvable author are grouped together
- **WHEN** an aggregating source's posts carry no x.com URL, no title handle and no author
- **THEN** all of them form one group and are threaded exactly as before, replies attaching whatever account they name

#### Scenario: Single-account feed behaviour is unchanged
- **WHEN** every post in an aggregating source resolves to the same author key and every reply answers that account
- **THEN** one group is formed and thread detection behaves as it did before author grouping

#### Scenario: Reply grouped with parent
- **WHEN** author a's posts are ["Parent post" at 17:00:00, "R to @a: reply" at 17:00:01]
- **THEN** both posts form one thread with "Parent post" as the parent

#### Scenario: Multiple replies grouped with parent
- **WHEN** author a's posts are ["Parent" at 17:00:00, "R to @a: reply 1" at 17:00:01, "R to @a: reply 2" at 17:00:02]
- **THEN** all 3 posts form one thread

#### Scenario: Multiple threads detected
- **WHEN** author a's posts are ["Thread A" at 10:00, "R to @a: A reply" at 10:01, "Thread B" at 15:00, "R to @a: B reply" at 15:01]
- **THEN** 2 threads are created: [Thread A + A reply] and [Thread B + B reply]

#### Scenario: Orphan reply becomes standalone thread
- **WHEN** an author's first post is "R to @a: orphan reply" with no preceding parent
- **THEN** it becomes a standalone thread with the reply as the parent

#### Scenario: Standalone posts become single-post threads
- **WHEN** author a's posts are ["Standalone A" at 10:00, "Standalone B" at 15:00] and neither starts with "R to @"
- **THEN** 2 single-post threads are created

### Requirement: RSS entry titles are plain text, and marker-based replies are normalised
`RssFeedFetcher` SHALL strip markup from an entry's title, using the same text extraction already applied to the entry body. Some feeds embed HTML in the `<title>` element, and without this the markup reaches the article title and from there the scoring and dedup prompts: Narro titles produced article titles reading `@ivanfioravanti: <span class="narro-reply-header">@Rawtrutholog</span>…`.

For feeds that mark a reply with markup rather than a title convention, `RssFeedFetcher` SHALL normalise the title to the `R to @<handle>: ` form that thread detection recognises. Narro wraps the post being replied to in a `narro-reply-header` span naming that account, and `NarroFeed.replyTarget` SHALL read the handle from the raw entry HTML. A title already starting with `R to @` SHALL NOT be prefixed again.

#### Scenario: Markup stripped from an entry title
- **WHEN** an entry's title is `@ivanfioravanti: <span class="narro-reply-header">@Rawtrutholog</span> some text`
- **THEN** the post's title is `@ivanfioravanti: @Rawtrutholog some text` with no markup

#### Scenario: Narro reply normalised to the R to @ convention
- **WHEN** an entry's body contains `<span class="narro-reply-header">@Rawtrutholog</span>` and its title is `@ivanfioravanti: LOL`
- **THEN** the post's title starts with `R to @Rawtrutholog: `

#### Scenario: Non-reply Narro entry left unprefixed
- **WHEN** an entry's body contains no `narro-reply-header` marker
- **THEN** the post's title is the cleaned title with no `R to @` prefix

#### Scenario: An already-prefixed title is not prefixed twice
- **WHEN** an entry's title already starts with `R to @someone: ` and its body contains a reply marker
- **THEN** the title is left with a single `R to @` prefix
