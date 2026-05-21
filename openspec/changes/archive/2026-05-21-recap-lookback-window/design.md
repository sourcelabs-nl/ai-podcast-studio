## Context

The episode continuity system currently passes a single previous episode's recap (2-3 sentences) to composers. This is insufficient to prevent topic repetition when different sources publish separate articles about the same news story across multiple days. The recap's purpose was narrative flow ("as we discussed last time..."), not deduplication.

Evidence: Episodes 45 (Mar 16) and 51 (Mar 18) both lead with the identical Claude Code 1M context story, sourced from different articles with different content hashes.

## Goals / Non-Goals

**Goals:**
- Prevent the LLM from presenting previously covered topics as new news
- Make the lookback window configurable per podcast (default: 7 episodes)
- Provide a global default via application config
- Minimal change footprint — reuse existing recap infrastructure

**Non-Goals:**
- Embedding-based semantic deduplication of articles (too complex for the gain)
- Topic-level deduplication before composition (would require an extra LLM call)
- Changing how recaps are generated (the 2-3 sentence format is fine)
- Frontend settings UI for this field (can be added later, but out of scope for now — API-only)

## Decisions

### 1. Pass multiple recaps as a numbered list, not a merged blob

**Decision:** Each recap stays separate and is numbered by recency (1 = most recent). This gives the LLM clear temporal context.

**Alternative considered:** Merging all recaps into a single summary. Rejected because it loses temporal ordering and would require an extra LLM call to summarize the summaries.

### 2. Change the composer instruction from "reference" to "deduplicate"

**Decision:** The prompt instruction shifts from "weave in references to previous episodes" to "do NOT repeat topics already covered unless there is a genuinely new development. If a topic appeared in any recent episode, either skip it or mention it in one sentence max with a back-reference."

The most recent episode (recap #1) retains the existing narrative flow instructions ("as we discussed last time..."). Recaps #2-N are used purely for deduplication.

### 3. Change composer signature from `String?` to `List<String>`

**Decision:** Change `previousEpisodeRecap: String?` parameter to `previousEpisodeRecaps: List<String>` (ordered most-recent-first). Empty list = no continuity context. This is cleaner than passing a nullable concatenated string.

### 4. Per-podcast override via `recap_lookback_episodes` column

**Decision:** Add a nullable `recap_lookback_episodes` INTEGER column to `podcasts`. When null, fall back to the global `app.episode.recap-lookback-episodes` (default: 7). This follows the same pattern as `targetWords`, `maxLlmCostCents`, and `maxArticleAgeDays`.

### 5. Repository query returns N most recent episodes with non-null recaps

**Decision:** Add `findRecentByPodcastId(podcastId, limit)` that returns episodes ordered by `generated_at DESC` with `recap IS NOT NULL`, limited to N. Episodes without recaps (pre-feature or failed generation) are skipped.

### 6. Recap lookback only considers GENERATED episodes

**Decision:** The `findRecentWithRecapByPodcastId` query filters by `status = 'GENERATED'`. Episodes in PENDING_REVIEW, DISCARDED, or FAILED states are excluded from the lookback window. This ensures only finalized episodes influence deduplication.

**Rationale:** Pending or discarded episodes may contain content that was rejected or will be regenerated. Including them in the lookback could cause the LLM to skip topics that were never actually published.

### 7. Regenerate button only for PENDING_REVIEW and DISCARDED episodes

**Decision:** The frontend Regenerate button is not shown for GENERATED episodes. Users must discard a GENERATED episode before regenerating. Additionally, the button is hidden if any episode on the same day has already been published, to prevent creating duplicate episodes for the same day's content.

**Rationale:** GENERATED episodes have completed TTS processing and may have been published. Allowing regeneration directly could lead to confusion about which version is the "real" episode. Discarding first makes the intent explicit.

## Risks / Trade-offs

- **Prompt length increase**: 7 recaps × ~50 tokens each = ~350 extra tokens. Negligible cost impact. → No mitigation needed.
- **Old episodes without recaps**: Episodes created before the recap feature have null recaps and will be skipped by the query. → Acceptable; these are old enough that topic overlap is unlikely.
- **LLM may still repeat**: The instruction is advisory — the LLM might still mention a topic if it appears in today's articles with significant new detail. → This is actually desirable; the instruction says "unless there is a genuinely new development."
