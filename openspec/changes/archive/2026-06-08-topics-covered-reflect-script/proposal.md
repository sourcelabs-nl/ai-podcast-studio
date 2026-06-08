## Why

The RSS feed's "Topics Covered" list (and the sources page's "Topics Covered" section) badly overstates what an episode actually discusses. For the 2026-06-08 episode the feed lists **42 topics**, but the ~12-minute script narrates only ~15. The compose LLM is given the full set of candidate topic labels and instructed to emit `topic_order` = "the labels in the order they are discussed", but it echoes back essentially all candidates rather than the discussed subset. Every linked article therefore keeps a non-null `topic_order`, and the feed faithfully renders all of them.

A read-time string match against the script is unreliable (token cross-contamination, e.g. "Google" from the Gemma topic falsely matching "Google Colab CLI"). Instead we reuse the recap LLM, which already reads the full script and the candidate topics in a single call, to determine the discussed subset semantically.

## What Changes

- The episode recap generator returns, alongside the recap, the subset of candidate topic labels actually discussed in the script (exact labels, determined by the same LLM call — no extra request).
- After recap, the pipeline clears `topic_order` (sets it `NULL`) on linked articles whose topic is **not** in the discussed subset. The article and its topic label are retained, so it simply moves from "discussed" to "background".
- `regenerateRecap` derives the candidate topic labels from the episode's existing links, so existing episodes are corrected by re-running it (no migration, no schema change).
- No change to `FeedGenerator` or `EpisodeSourcesGenerator` logic: the feed already lists only `topic_order != null` topics, and the sources page already splits "Topics Covered" (`topic_order != null`) from "Additional Sources" (`topic_order == null`). Both become correct once the data is corrected.

## Capabilities

### Modified Capabilities

- `podcast-feed`: "Topics Covered" reflects only topics actually discussed in the script.
- `episode-show-notes`: recap generation additionally identifies the discussed topic subset and prunes non-discussed topics to background.

## Impact

- `src/main/kotlin/com/aisummarypodcast/llm/EpisodeRecapGenerator.kt` (+ `RecapResult`)
- `src/main/kotlin/com/aisummarypodcast/podcast/EpisodeService.kt` (`generateAndStoreRecap`, `regenerateRecap`)
- `src/main/kotlin/com/aisummarypodcast/store/EpisodeArticleRepository.kt` (new `clearTopicOrderForUncoveredTopics` update)
- Tests for the above. No DB migration; `topic_order` semantics tighten from "considered" to "discussed".
