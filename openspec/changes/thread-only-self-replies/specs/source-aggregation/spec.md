## MODIFIED Requirements

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
