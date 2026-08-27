## Context

`RssFeedFetcher.fetch` mapped each surviving feed entry to a `Post`, and its first line was `val title = entry.title ?: return@mapNotNull null`. Nitter's RSS carried a title per tweet (the tweet text), so every X source satisfied that for years.

Mastodon does not. A Mastodon post has content and no title, and its RSS reflects that: zero of the 20 items in `https://zpravobot.news/@OpenAI.rss` contain a `<title>` element. When the X sources were repointed there, each poll parsed the feed correctly, dropped all 20 entries at the title check, and logged "Fetched 0 new entries" with no failure and no warning. The source looked healthy while producing nothing.

`Post.title` is not decorative: it is what the scorer and composer see first, and it is displayed in the dashboard. So the fix has to produce a genuinely readable title, not a placeholder.

## Goals / Non-Goals

**Goals:**

- Store entries from feeds that omit `<title>`, so Mastodon-based X mirrors work as ordinary RSS sources.
- Produce a title that reads like a headline rather than a truncated paragraph.
- Leave every feed that does supply titles behaving exactly as before.

**Non-Goals:**

- Generating a title with an LLM. The scoring stage already reads the full body; spending a model call per entry to title it would add cost and latency to polling for no gain.
- Special-casing Mastodon by host. Any feed may omit titles, and a host check would silently fail on the next mirror service.
- Changing `contentHash`, which is derived from the body and so is unaffected by title derivation.

## Decisions

**Derive from the body, in the fetcher.** The body is already extracted and HTML-stripped a few lines below the title check, so the fix is to reorder: extract the body first, then resolve the title from `entry.title` or the body. Deriving here rather than in `SourcePoller` keeps every consumer of `fetch()` (poller, source validation) consistent, and keeps `Post` non-null on title.

**First sentence, else word-boundary truncation.** A sentence break within the first 100 characters gives the cleanest headline, and social posts usually open with one. Falling back to a word boundary avoids cutting mid-word, and the ellipsis signals truncation to both the reader and the composer. A minimum length of 20 characters stops a break like "Hi." or a stray early period from producing a uselessly short title; below that the text is truncated instead.

**Blank titles count as missing.** `entry.title?.takeIf { it.isNotBlank() }` rather than a null check, because some feeds emit `<title></title>`, which would otherwise store an empty title and produce a blank headline.

**Alternatives considered:** using the entry link or the feed's channel title as the title. Both produce identical or near-identical titles across every entry in a feed, which would break the content-hash-independent readability of the dashboard and give the scorer nothing to work with.

## Risks / Trade-offs

- **A derived title duplicates the opening of the body, so the scorer sees the first sentence twice** → Harmless in practice: the scorer reads the body for substance and the duplication is a sentence at most. Nitter had the same property, since its titles were the tweet text.
- **Truncation could cut mid-entity (a model name, a URL)** → The word-boundary rule prevents mid-word cuts, and the full text always remains in the body, which is what the summarizer actually reads.
- **A feed that emits a useless one-word body now produces a useless title instead of being dropped** → Preferable to silently discarding content, and the relevance scorer filters low-value items downstream anyway.

## Migration Plan

No data migration and no configuration change. Existing posts keep their stored titles. Sources pointing at titleless feeds begin producing posts on their next poll; those already polled with `lastSeenId` unchanged will pick up entries newer than that timestamp.

## Open Questions

- Whether the retweet prefixes that Mastodon mirrors emit (`Sam Altman 𝕏🔁 @OpenAI:`) should be stripped from derived titles is unresolved. They are noise in the dashboard but scored normally, so this is deferred until it demonstrably hurts output quality.
