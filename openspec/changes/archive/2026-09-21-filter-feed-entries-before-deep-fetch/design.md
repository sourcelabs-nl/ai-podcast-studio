## Context

`RssFeedFetcher.fetch` filters entries by `lastSeenId` and category, then maps each survivor to a `Post`. That mapping calls `resolveBody`, which deep-fetches the entry's linked article. So the cost of a fetch is proportional to the number of entries that survive the *filters inside the fetcher*, not the number the caller eventually keeps.

Both callers got that wrong in different ways:

- `SourceService.validateRssUrl` fetched with deep-fetch on and no lower bound, to answer a yes/no question ("does this feed parse and yield an item?").
- `SourcePoller` passed `source.lastSeenId` through and applied the max-age and pre-creation bounds afterwards, in the save loop.

For a normal feed both are cheap, because a feed of 20 entries deep-fetches at most 20 articles. For `openai.com/news/rss.xml`, which carries 1153 entries, creation ran over 12 minutes and never returned, with ~820 article crawls logged before the attempt was abandoned.

## Goals / Non-Goals

**Goals:**

- Make adding any feed cheap regardless of how much history it exposes.
- Stop deep-fetching entries that are certain to be discarded.
- Keep the save-loop checks, which remain the only bounds for source types that bypass the RSS filter.

**Non-Goals:**

- Making deep-fetch itself faster or concurrent. The problem is how many articles are fetched, not how fast each one is.
- Changing what `lastSeenId` means or how it is written back. It is still the newest publish time actually saved.
- Bounding entries with no publish date. The fetcher keeps those regardless of the lower bound, which is existing behaviour and matters for feeds with no dates at all.

## Decisions

**Pass the bound through `lastSeenId` rather than adding a parameter.** `fetch()` already takes five arguments, and this project wraps parameter lists in a data class beyond four or five. Introducing a `FeedFetchRequest` would have been the rule-following move, but it would touch roughly 25 call sites across the poller, source validation, and four test classes, which is a large refactor to carry a bug fix.

The framing that avoids the choice: the age cutoff, the creation time and `lastSeenId` are all the same thing, a lower bound on entry publish time, and the fetcher already applies exactly that semantics to the `lastSeenId` argument. So `rssFetchFloor` takes the latest of the three and passes it in the existing slot. No signature change, no new concept in the fetcher, and the call site reads as what it is.

**Keep the save-loop checks.** They are now redundant for RSS but not dead: `TWITTER` and `WEBSITE` posts never pass through the fetcher's entry filter, so removing them would drop the age bound for those types entirely.

**Validation disables deep-fetch rather than bounding it.** Validation genuinely wants the newest entries regardless of age, so a time bound would be wrong; it just has no use for article bodies. `deepFetch = false` is the accurate expression of that.

## Risks / Trade-offs

- **A reader expects `lastSeenId` to be the source's stored value and finds a computed instant** → Mitigated by the helper's name and its doc-comment, which state that it is a floor and why it is passed there. The alternative (a sixth positional parameter) was judged worse for readability at the call site.
- **A first poll now returns nothing for a feed whose entries all predate source creation** → That is the pre-existing intended behaviour (the save loop already discarded exactly those posts); it now costs nothing instead of a full crawl. Note the guard is one poll deep: the first poll sets `last_polled` while saving nothing, so a later poll with a null `last_seen_id` falls back to the age cutoff and can admit the backlog.
- **Bounds are enforced in two places, which can drift** → Accepted deliberately, since the second place is load-bearing for other source types. The spec states both.

## Migration Plan

No data migration, no schema change, no configuration change. Ships with the application build; a restart is sufficient. Rollback is reverting the two source files.

## Open Questions

- Whether entries with no publish date should be bounded at all is unresolved. They currently bypass every time bound and are always deep-fetched, which is correct for undated feeds but means an undated archive would still be crawled in full.
