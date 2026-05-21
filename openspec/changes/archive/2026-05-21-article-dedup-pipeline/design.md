## Context

The podcast pipeline currently has three compounding issues causing episodes to repeat content:

1. **Discard resets articles unconditionally** — `EpisodeService.discardAndResetArticles()` resets `isProcessed = false` on all linked articles without checking whether those articles are also linked to GENERATED episodes with publications. This causes articles to re-enter the pipeline indefinitely through regenerate/discard cycles.

2. **No article age gate** — When a new source is added, all its historical content enters the pipeline. There is no cutoff based on the latest published episode's timestamp.

3. **No semantic dedup** — The `content_hash` dedup only catches byte-identical text. Multiple sources covering the same story produce different articles that all reach the composer. The recap-based dedup is a lossy 2-3 sentence summary that the composer can ignore.

4. **Duplicated logic** — Article eligibility logic is scattered across `LlmPipeline.run()`, `recompose()`, `preview()`, and `EpisodeService.discardAndResetArticles()`, each implementing its own version of "which articles are eligible" and "what to do after composition." This caused the `isProcessed` bug — `recompose()` never marks articles as processed because it has its own code path.

Evidence: Episode 52's 101 candidate articles were 100% repeats from previous episodes (confirmed by simulation with a topic clustering agent).

## Goals / Non-Goals

**Goals:**
- Prevent articles from re-entering the pipeline after being used in a published episode
- Prevent old content from new sources from flooding the pipeline
- Prevent cross-source topic overlap in composed scripts
- Detect continuation stories ("same topic, genuinely new development") and annotate them for the composer
- Consolidate article eligibility and lifecycle logic into a single component
- Reduce the number of articles reaching the expensive compose model (100 → ~15-25)

**Non-Goals:**
- Embedding-based semantic dedup (too complex for the gain)
- Changing the recap generation (stays as-is for publication descriptions)
- Changing the article scoring/summarization stage
- Frontend changes

## Decisions

### 1. Centralize article eligibility in an ArticleEligibilityService

**Decision:** Create an `ArticleEligibilityService` that owns all article selection and lifecycle logic. `LlmPipeline.run()`, `recompose()`, and `preview()` all delegate to this service. `EpisodeService.discardAndResetArticles()` delegates the reset guard check to this service.

**Alternative considered:** Fixing each code path individually. Rejected because it perpetuates the duplication that caused the bug.

### 2. Discard reset guard — check for published GENERATED episodes

**Decision:** When discarding an episode, only reset `isProcessed = false` for articles that are NOT linked to any GENERATED episode that has at least one publication with status PUBLISHED. Query: join `episode_articles` → `episodes` (status = GENERATED) → `episode_publications` (status = PUBLISHED) for each article.

**Alternative considered:** Check if the article is linked to any non-discarded episode (regardless of publication status). Rejected because an unpublished GENERATED episode's articles should still be resettable — only published content is "final."

### 3. Article age gate — cutoff at latest published episode's generated_at

**Decision:** When selecting candidate articles, exclude articles whose `published_at` (or `created_at` for articles without a publication date) is before the latest GENERATED+published episode's `generated_at`. This prevents old content from new sources from flooding the pipeline while still allowing new content from new sources.

**Alternative considered:** Using the podcast's `lastGeneratedAt` as the cutoff. Rejected because `lastGeneratedAt` tracks the most recent generation attempt, not the most recent published episode — regenerated/discarded episodes would move the cutoff forward incorrectly.

### 4. Topic dedup filter as a new pipeline step

**Decision:** Add a `TopicDedupFilter` component that runs between scoring and composition. It receives candidate articles (title + summary) and historical articles from recent episodes (via `episode_articles` join). It makes a single cheap LLM call (filter model) to:

1. Cluster articles by topic (both today's candidates and historical)
2. For each cluster, classify as NEW (no historical overlap) or CONTINUATION (historical overlap with genuinely new information)
3. For NEW clusters: select top 3 most comprehensive/complementary articles
4. For CONTINUATION clusters with new development: select top 3, annotate with `previousContext` describing what was covered before
5. For CONTINUATION clusters with no new development: select nothing (skip entirely)

Single-article clusters pass through untouched — these are often the most unique/valuable articles.

**Output format:** Structured JSON with `clusters` array. Each cluster has `topic`, `status` (NEW/CONTINUATION), `previousContext` (for CONTINUATION), `candidateArticleIds`, and `selectedArticleIds`.

**Alternative considered:** Two separate LLM calls (cluster first, then select per cluster). Rejected because the clustering + selection task is simple enough for a single call, and the input size (~16K tokens for 100 candidates + 100 historical) is well within cheap model context limits.

### 5. Composer receives annotated articles with [FOLLOW-UP] headers

**Decision:** The dedup filter's output is transformed into the composer's article block. CONTINUATION topics get a `[FOLLOW-UP: ...]` header above their article cluster, giving the composer explicit context about what was covered before and what's new. This replaces the recapBlock entirely.

Format in composer prompt:
```
[FOLLOW-UP: Gemini 2.5 was released with benchmarks in a recent episode — today's articles cover new pricing details]

8. [The Verge] Gemini 2.5 pricing announced
   Google announced Gemini 2.5 pricing at $X per million tokens...
```

**Alternative considered:** Keeping the recapBlock as a safety net alongside the annotations. Rejected because the annotations provide strictly more information than the compressed recaps, and keeping both would send conflicting signals to the LLM.

### 6. Recaps remain for publication descriptions only

**Decision:** Continue generating recaps via `EpisodeRecapGenerator` after episode creation. The recap is used for feed.xml episode descriptions and SoundCloud descriptions. The recap is no longer passed to composers — the `previousEpisodeRecaps` parameter and `recapBlock` are removed from all three composers.

The `recapLookbackEpisodes` config and `fetchRecentRecaps()` in `LlmPipeline` are removed since they are no longer needed.

### 7. Historical article lookback uses episode_articles join

**Decision:** The dedup filter fetches historical articles by joining `episode_articles` → `articles` for recent GENERATED episodes (using the same lookback window concept — last N episodes or configurable). This provides the actual article titles and summaries that were used in each episode, which is far richer than the compressed recap.

**Token budget estimate:**
- Today's candidates: ~100 articles × ~80 tokens (title + summary) ≈ 8,000 tokens
- Historical: ~7 episodes × ~15 articles × ~80 tokens ≈ 8,400 tokens
- Total: ~16,400 tokens — well within cheap model context

### 8. Top 3 selection per cluster

**Decision:** For clusters with more than 3 articles, the LLM selects the 3 most comprehensive and complementary articles (different sources, different angles). For clusters with ≤3 articles, all are kept. Single-article clusters (often unique, high-value content) pass through untouched.

## Risks / Trade-offs

- **Extra LLM call per pipeline run**: The dedup filter adds one cheap model call (~16K tokens input). Cost is negligible compared to the compose call, and it significantly reduces the compose call's input size (100 → ~15-25 articles). → Net cost may actually decrease.

- **Dedup filter quality depends on summaries**: The clustering uses article summaries generated in Stage 1. If summaries are poor, clustering will be poor. → Current summaries are well-structured and factual (verified by inspection), so this is low risk.

- **Historical lookback window**: Using the last N GENERATED episodes means the lookback shrinks if episodes are generated more frequently. → Configurable via existing `recapLookbackEpisodes` setting repurposed for the dedup filter.

- **Edge case: article linked to both published and unpublished episodes**: The discard reset guard correctly handles this — it checks each article individually for published episode links. → No special handling needed.

- **Race condition: concurrent pipeline runs**: If two pipeline runs execute simultaneously, they could both select the same articles before either marks them as processed. → Existing issue, not introduced by this change. Could be addressed separately with optimistic locking.
