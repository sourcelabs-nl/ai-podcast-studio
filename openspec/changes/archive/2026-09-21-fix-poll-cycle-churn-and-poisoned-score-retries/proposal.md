## Why

Three defects visible in `app.log` over a single four-hour window, all of them self-perpetuating.

**Post age was measured on the wrong column.** Article cleanup deletes unprocessed articles whose `publishedAt` is past the retention window, but the unlinked-post queries and the unlinked-post cleanup both measured age on `created_at`. A feed that back-dates its entries therefore produced posts that no cleanup could ever reach: aggregation turned each one into an article, the next cleanup pass deleted that article for being too old, `ON DELETE CASCADE` dropped the `post_articles` link, and the following poll re-aggregated and re-scored the very same post. One feed churned 360 posts through this loop every thirty minutes. The `articles` primary key had reached 243,697 with under 10,000 live rows, and 420 posts had been stuck in the loop since June.

**A retry sent the byte-identical prompt.** `CachingChatModel` keys on prompt text and only refuses to cache blank completions, so a model that answered with prose instead of JSON had that unparseable answer cached. Every retry replayed it from cache and failed the same way in about two milliseconds, making the exponential backoff meaningless. Because a failed article keeps a null `relevanceScore`, `findUnscoredBySourceIds` picked it up again on every later run: nine articles burned three attempts each, every cycle, and could never be scored.

**A lost optimistic-lock race was recorded as a poll failure.** When a scheduled poll round overlaps another poll of the same source, the loser's `sources` update fails with `OptimisticLockingFailureException`. Raised on the success path, that exception was caught by the surrounding handler and treated as a *fetch* failure, so a healthy source had its `consecutiveFailures` incremented and could eventually be auto-disabled. Seven of these landed inside one minute.

## What Changes

- The unlinked-post queries (`findUnlinkedBySourceIds`, `findUnlinkedSince`) and `deleteOldUnlinkedPosts` SHALL measure post age on `COALESCE(published_at, created_at)`, matching how article cleanup measures article age. A post too old to survive as an article is therefore never re-aggregated, and becomes reclaimable by cleanup instead of immortal.
- `ArticleScoreSummarizer` SHALL send a distinct prompt on every attempt, appending a correction naming the attempt number from the second attempt on. This both busts the cache key, so each retry is a real call rather than a replay, and tells the model that its previous answer could not be parsed.
- `SourcePoller` SHALL persist its post-poll source state through a helper that, on `OptimisticLockingFailureException`, re-reads the row and reapplies the update once. Losing the race SHALL NOT be recorded as a poll failure. If the source was deleted mid-poll, the poll state SHALL be discarded rather than an error raised.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `post-store`: the unlinked-post query and old-post cleanup requirements switch from `created_at` to the published date, with `created_at` as the fallback.
- `source-aggregation`: the time-windowed aggregation requirement follows the same column change.
- `llm-processing`: the score/summarize retry requirement gains the distinct-prompt-per-attempt behaviour.
- `source-polling-backoff`: the failure-tracking requirement gains the exclusion for a lost optimistic-lock race.

## Impact

- Backend: `PostRepository` (three queries), `ArticleScoreSummarizer` (new `promptForAttempt` and `jsonOnlyCorrection`), `SourcePoller` (new `saveSourceState` helper; the failure branch now derives its counter from the row it is given rather than the stale copy).
- Tests: `PostRepositoryTest`, `ArticleScoreSummarizerTest`, and `SourcePollerTest` gain coverage for each of the three fixes.
- No schema change: the retention columns already exist, and no migration is needed. The stale post backlog clears itself on the first cleanup pass after deploy.
- No API, frontend, or configuration change.
- Not addressed here: the 35 `nitter.net` sources that all fail because the host serves an HTML error page instead of RSS. That is source configuration, not code.
