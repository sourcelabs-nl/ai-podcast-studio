## Context

`topic_order` on `episode_articles` is set during compose from the LLM's emitted `|||TOPIC_ORDER|||` block, which is supposed to list only topics discussed in the script but in practice echoes nearly all candidate labels. The feed (`articles.filter { it.topicOrder != null }`) and the sources page (discussed vs additional) both key off `topic_order`, so the over-listing propagates to both surfaces.

## Goals / Non-Goals

**Goals:**
- "Topics Covered" reflects what the script actually discusses.
- Reliable (semantic) determination, retroactive for existing episodes, no extra LLM call, no schema change.

**Non-Goals:**
- Changing the compose prompt/contract (separate concern; this corrects the data after the script exists).
- Changing feed or sources-page rendering logic.

## Decisions

- **Recap LLM determines the discussed subset.** `EpisodeRecapGenerator` already receives `scriptText` + `topicLabels` and makes one LLM call. It will additionally return `coveredTopics: List<String>` — the candidate labels actually discussed — using a delimited block appended to its response (parsed like the existing `TopicOrderExtractor`, exact-label match). No extra request.
- **Prune by clearing `topic_order`.** After recap, set `topic_order = NULL` for links whose `topic` is not in `coveredTopics`. The article + topic label remain, so it becomes a "background" source. This reuses existing storage and needs no migration.
- **Safety guard.** If `coveredTopics` is empty (LLM returned nothing / parse failed), make no changes — never wipe all topics on a parse miss.
- **Retroactive path.** `regenerateRecap` derives the candidate labels from existing links (distinct non-null-`topic_order` topics, in order) and passes them in, so re-running it on an existing episode (e.g. 140) corrects the data.

## Risks / Trade-offs

- [LLM omits a genuinely discussed topic] → it moves to "Additional Sources" / drops from the feed; the article is still linked and on the sources page. Acceptable, and far better than 42 vs 15.
- [`topic_order` semantics change from "considered" to "discussed"] → documented; both consumers already treat `topic_order != null` as "discussed".
