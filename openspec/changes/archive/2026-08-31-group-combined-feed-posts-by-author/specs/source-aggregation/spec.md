## MODIFIED Requirements

### Requirement: Thread detection
The aggregator SHALL group an aggregating source's posts by author BEFORE detecting threads, and SHALL detect threads independently within each author's posts.

The author key SHALL be resolved from the first of these that yields a value:
1. The `<handle>` path segment of a post URL of the form `x.com/<handle>/status/<id>` (or `twitter.com`), lowercased
2. A leading `@handle:` prefix in the post title, lowercased
3. The post's `author` field, trimmed and lowercased

Posts with no resolvable author key SHALL form a single group together, which reproduces the previous behaviour for feeds carrying no author information.

Author grouping is required because a source is no longer the same thing as an account. A combined feed (such as the Narro feed that replaced the per-account X sources) merges many accounts into one RSS document, and without grouping the reply-attachment rule below splices posts by different people into one article.

Within each author's posts, a post SHALL be considered a reply if its title starts with "R to @" (case-sensitive). Posts SHALL be sorted by `publishedAt` ascending before grouping. Each non-reply post starts a new thread. Each reply post SHALL be attached to the most recent preceding non-reply post by that same author. If a reply has no preceding parent within its author's posts, it SHALL be treated as a standalone thread.

Feeds that mark replies with their own markup rather than an `R to @` title SHALL have that title normalised at ingestion (see the RSS ingestion requirement), so thread detection needs only the one convention.

#### Scenario: Posts grouped by author before threading
- **WHEN** an aggregating source's posts are ["@a: Parent" at 10:00 from x.com/a/status/1, "@b: Parent" at 10:01 from x.com/b/status/2, "R to @x: reply" at 10:02 from x.com/a/status/3]
- **THEN** the reply joins author a's thread, and author b's post remains its own thread

#### Scenario: A reply never attaches across authors
- **WHEN** author b's post is the most recent preceding post in time, but the reply's URL handle is author a
- **THEN** the reply is attached to author a's preceding thread, not to author b's post

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
- **THEN** all of them form one group and are threaded exactly as before

#### Scenario: Single-account feed behaviour is unchanged
- **WHEN** every post in an aggregating source resolves to the same author key
- **THEN** one group is formed and thread detection behaves as it did before author grouping

#### Scenario: Reply grouped with parent
- **WHEN** one author's posts are ["Parent post" at 17:00:00, "R to @user: reply" at 17:00:01]
- **THEN** both posts form one thread with "Parent post" as the parent

#### Scenario: Multiple replies grouped with parent
- **WHEN** one author's posts are ["Parent" at 17:00:00, "R to @user: reply 1" at 17:00:01, "R to @user: reply 2" at 17:00:02]
- **THEN** all 3 posts form one thread

#### Scenario: Multiple threads detected
- **WHEN** one author's posts are ["Thread A" at 10:00, "R to @user: A reply" at 10:01, "Thread B" at 15:00, "R to @user: B reply" at 15:01]
- **THEN** 2 threads are created: [Thread A + A reply] and [Thread B + B reply]

#### Scenario: Orphan reply becomes standalone thread
- **WHEN** an author's first post is "R to @user: orphan reply" with no preceding parent
- **THEN** it becomes a standalone thread with the reply as the parent

#### Scenario: Standalone posts become single-post threads
- **WHEN** one author's posts are ["Standalone A" at 10:00, "Standalone B" at 15:00] and neither starts with "R to @"
- **THEN** 2 single-post threads are created

## ADDED Requirements

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
