## Context

Three independent bugs share one shape: a component recovers from a failure in a way that guarantees the failure recurs. Each was diagnosed from `app.log` and confirmed against the production database.

The churn loop is the most consequential, and its cause is a disagreement between two date columns. `posts.created_at` is the ingestion timestamp, set by `SourcePoller` when it saves the row. `posts.published_at` is what the feed claimed. `ArticleRepository.deleteOldUnprocessedArticles` measures article age on `published_at`; the post-side queries measured it on `created_at`. For a well-behaved feed the two columns move together and the disagreement is invisible. For a feed that back-dates entries, or one polled for the first time long after publication, `created_at` stays recent forever while `published_at` sits outside the retention window, and the post becomes permanently eligible for an aggregation whose output is permanently ineligible to survive.

## Goals / Non-Goals

**Goals:**

- A post that cannot produce a surviving article is never aggregated, and is reclaimable by cleanup.
- Every scoring attempt is a real model call, so a retry can actually change the outcome.
- Losing a concurrent-poll race leaves a healthy source healthy.

**Non-Goals:**

- Reclaiming the `articles` primary key space already burned by rolled-forward inserts. A `Long` id has ample room, and renumbering would break every `episode_articles` reference.
- Preventing the poisoned cache entry from being written in the first place. `CachingChatModel` decorates a generic `ChatModel` and cannot know what shape any given caller expects, so it can only reject completions that are unusable to everyone (which is why the existing guard covers blank text and nothing more). Bounding the damage at the caller, which does know the expected shape, is the smaller and more honest fix.
- Purging poisoned entries already in `llm_cache`. `LlmCacheCleanup` expires entries on `app.llm-cache.max-age-days`, so they age out on their own, and the retry no longer depends on them being gone.
- De-duplicating concurrent polls of the same source. The optimistic-lock retry makes the race harmless; serialising polls per source would need a lock the scheduler does not currently have, for a race that is rare and now benign.

## Decisions

**`COALESCE(published_at, created_at)` rather than `published_at` alone.**

`posts.published_at` is nullable: some feeds give no date, and `SourcePoller` explicitly saves those posts rather than dropping them. Filtering on `published_at` alone would silently exclude every dateless post from aggregation, which is a worse bug than the one being fixed. The fallback keeps those posts on their ingestion clock, which is the only clock they have.

**Align the post side onto the article side, not the reverse.**

The alternative was to make `deleteOldUnprocessedArticles` measure age on some ingestion column, so back-dated content would be retained rather than discarded. Rejected: every other article query (`findAllSince`, `findUnprocessedSince`) already filters on `published_at`, because the product question is "is this news recent enough to talk about", not "did we happen to fetch it recently". Retention should answer that same question. Moving the post side is also the change that makes the two sides agree in *both* directions: stale posts stop being re-aggregated *and* start being deleted.

**Bust the cache key by naming the attempt in the prompt.**

The retry needs a prompt that differs from the failed one. Options considered:

- *A cache-bypass flag on the retry.* Spring AI's `ChatOptions` has no slot for caller metadata, so this would mean either a thread-local read by `CachingChatModel` or a second non-caching `ChatClient` from the factory. Both add machinery, and a non-caching client would never write the good response either.
- *Invalidating the poisoned entry before retrying.* Correct, but the summarizer holds a `ChatClient` and never sees the `Prompt`, so it would have to reconstruct `CachingChatModel`'s key format. Two places computing the same key is exactly the kind of duplication that drifts.
- *Appending a correction that names the attempt.* Chosen. The attempt number makes attempts 2 and 3 distinct from each other as well as from attempt 1, so no attempt in the sequence can replay a cached failure. The correction text is also independently useful: the observed failures were models emitting reasoning prose or a bare `We ...` ahead of the JSON, which is precisely what an explicit "raw JSON only" instruction addresses.

**The lock-failure retry reapplies the update to the row it re-read.**

The failure branch computes `consecutiveFailures + 1`. Reapplying the stale copy's arithmetic after a re-read would discard whatever the concurrent writer recorded, so `saveSourceState` takes the update as a function of a `Source` and calls it again with the freshly read row. That also keeps the auto-disable threshold check honest, since it sees the true running count.

## Risks / Trade-offs

- **A feed that back-dates entries beyond the retention window now yields nothing at all.** → This is the intended behaviour and was already true of the article side; the change only stops the pipeline from paying for it repeatedly. Operators who want that content can raise `app.source.max-article-age-days` or the podcast's `maxArticleAgeDays`.
- **`getUpcomingContent` counts unlinked posts on the published clock now, so back-dated posts disappear from the upcoming-content preview.** → Correct, and more accurate than before: the preview now agrees with what the pipeline will actually pick up, and it already filtered its *articles* on `published_at`.
- **The correction text is sent on every retry, so a retry costs slightly more input tokens.** → Two sentences against an article body; immaterial next to the cost of an article that can never be scored.
- **A poisoned cache entry still wastes attempt 1 until it expires.** → Bounded and cheap: a cache hit fails in about two milliseconds and costs nothing. The article is scored on attempt 2, so `relevanceScore` is set and it is never re-queued.
- **`saveSourceState` retries only once.** → A second consecutive loss would need three concurrent polls of one source. It would surface as before rather than looping, which is the right failure mode for an unexpected level of contention.
- **The auto-disable warning can log twice if the update function runs a second time.** → Accepted; the retry path is rare and the second line reflects the decision actually persisted.
