## Why

Episode 126 of *The Daily Agentic AI Podcast* exposed a structural defect in the rapid-fire segment: the composer fed ~18 low-weight articles into a 263-word budget with the instruction "one to two sentences each", which is arithmetically impossible. The LLM resolved the contradiction by cramming 3-5 stories into single expert turns, and a genuinely high-impact item (an AI autonomously solving an Erdős problem from 1946) was demoted into a one-line mention alongside obscure surveys, prompting the interviewer to ad-lib *"That deserved a full segment!"* Two root causes: no item cap and no priority ordering inside the rapid-fire tier.

## What Changes

- Add a per-podcast `rapidFireMaxItems` field (nullable INTEGER) that caps how many articles the rapid-fire tier emits. When null, falls back to a global default (`app.compose.rapid-fire-max-items: 6`).
- Rank rapid-fire articles by `(bucket weight desc, article relevance score desc, article id asc)` and keep only the top `rapidFireMaxItems`. Dropped articles are excluded from the episode entirely (no "also today" mention).
- Replace the vague "one to two sentences per article" instruction with an arithmetic-consistent per-item word budget (`rapidFireWordBudget / keptCount`) and an explicit ordered list of kept articles by title in the composer prompt, so the LLM knows exactly how much room each item gets and which to cover first.
- Expose `rapidFireMaxItems` on the podcast Create/Update/Response API and in the frontend podcast settings form.
- **BREAKING (internal API only)**: `SubtopicPlan.from` gains a required `rapidFireMaxItems: Int` parameter; `SubtopicPlan` gains a `rapidFireOrder: List<RapidFireItem>` field; `RapidFireStyle` enum replaces `label` + `instruction` with a single `intro` string.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `weighted-subtopics`: the "Subtopic-aware pacing in composer" requirement gains cap + ranking + ordered enumeration + per-item word budget. A new "Rapid-fire item cap and ranking per podcast" requirement is added.

## Impact

- **Code**: `SubtopicPlan.kt`, three composers (`BriefingComposer`, `DialogueComposer`, `InterviewComposer`), `Podcast` entity, `AppProperties.ComposeProperties`, `PodcastDtos`, `PodcastMappers`, `PodcastController`, `application.yaml`.
- **Database**: Flyway migration `V58__add_podcast_rapid_fire_max_items.sql` adds nullable `rapid_fire_max_items INTEGER` column to `podcasts`.
- **API**: `rapidFireMaxItems` field added to podcast Create/Update/Response payloads. Backwards-compatible (nullable, omitted = use system default).
- **Frontend**: New "Rapid-fire Max Items" input in the podcast Compose Settings tab.
- **Behavior**: Existing podcasts continue to function with the system default cap of 6 until explicitly configured. Episodes generated under the old behavior remain unchanged.
- **Tests**: `SubtopicPlanTest` extended with cap, ranking, tie-break, ordered-enumeration, and per-item-budget cases (14 tests total, all passing).
