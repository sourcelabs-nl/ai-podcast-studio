## Context

`SourceAggregator.aggregatePosts()` currently merges all posts from a Twitter/Nitter source into a single article. This produces an article with a generic "Posts from @user" title and the source RSS feed as the URL, which is useless as a source link. The article body contains all posts (often 10-15) joined with separators, forcing the LLM to read everything including irrelevant posts (Stitch demos, Lyria music, etc.) when only 2-3 threads are relevant.

Individual tweet articles (non-aggregated) have a different problem: their title is the full tweet text, which works well for LLM processing but is too long for display in the sources HTML page.

## Goals / Non-Goals

**Goals:**
- Aggregate posts by thread instead of bundling all posts per source
- Each thread article gets the parent tweet's URL (not the RSS feed URL)
- Rewrite nitter.net URLs to x.com URLs in article URLs
- Truncate long article titles at display time in the sources HTML
- Backfill the last Agentic AI podcast episode

**Non-Goals:**
- Changing how non-aggregated sources work
- Truncating titles at the storage level (LLM benefits from full text)
- Migrating all historical aggregated articles
- Changing the feed generator's article display (separate concern)

## Decisions

### 1. Thread detection via reply prefix and temporal proximity

**Decision:** Detect replies by checking if the post title starts with "R to @". Group replies with the nearest preceding non-reply post from the same source (within a 60-minute window). Posts are processed in chronological order. Standalone posts (no replies) become single-post threads.

**Alternatives considered:**
- *Parse Twitter status IDs for in-reply-to*: Not available in the RSS feed data. The nitter RSS feed doesn't expose reply-to metadata.
- *Group by identical timestamp*: Too brittle. Replies sometimes have timestamps 1-60 seconds after the parent.

**Approach:** Sort posts by `publishedAt` ascending. Iterate: if a post title does NOT start with "R to @", start a new thread. If it does, attach to the current thread (the most recent parent). If no parent exists (orphan reply), treat it as a standalone thread.

### 2. One article per thread

**Decision:** Each thread produces one article:
- `title`: The parent post's title (full text, not truncated, for LLM benefit)
- `url`: The parent post's URL, with nitter.net rewritten to x.com
- `body`: Parent post body + reply bodies joined with `\n\n---\n\n`
- `author`: The parent post's author
- `publishedAt`: The parent post's `publishedAt`
- `contentHash`: SHA-256 of the combined body

### 3. Nitter→x.com URL rewriting

**Decision:** When constructing the article URL from a post URL, replace `nitter.net` with `x.com` in the hostname. This applies only to article URLs, not to source URLs or post URLs (those remain as-is in the database).

### 4. Title truncation at display time only

**Decision:** `EpisodeSourcesGenerator` truncates article titles longer than 120 characters with "..." when rendering HTML. The full title remains in the `articles` table for LLM processing. This also benefits the `FeedGenerator` if applied there.

### 5. Backfill strategy

**Decision:** Manual backfill for the last Agentic AI podcast episode only. Delete the existing aggregated articles for that episode's linked sources, re-run aggregation for those posts, and regenerate the sources HTML. No automated migration for historical episodes.

## Risks / Trade-offs

- **Thread detection heuristic may misgroup**: If a source posts a reply to someone else's tweet (not a self-reply thread), it would be grouped with the preceding parent. In practice, nitter RSS feeds for a single user almost always show self-reply threads, so this is low risk.
- **More articles per source per cycle**: Instead of 1 aggregated article, a source might produce 5-8 thread articles. This increases the number of articles the LLM scores, but each is smaller and more focused, so total token usage should be comparable or lower.
- **Existing aggregated articles remain**: Old episodes keep their "Posts from @user" articles. Only the last episode is backfilled. This is acceptable since the improvement is forward-looking.
