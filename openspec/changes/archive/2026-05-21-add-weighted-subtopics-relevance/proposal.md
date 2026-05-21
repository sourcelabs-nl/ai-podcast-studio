## Why

Today a podcast has a single `topic` string and one global `relevanceThreshold`. Every article that clears the threshold is treated equally in the script. In practice some areas inside a topic deserve full coverage (e.g. "LLM model releases") while others are interesting but should only get a one-line mention ("general AI news"). Editors currently have no way to express this pacing intent — the only knob is "in or out."

This change lets a podcast define a list of weighted **subtopics** (importance 1-10) under its main topic. Important subtopics get full segments; low-weight subtopics are rolled into an explicit "and in brief" rapid-fire segment at the end of the script. Pacing follows editorial intent without giving up on covering the long tail.

## What Changes

- Add `subtopics: Map<String, Int>` and `rapidFireWeightThreshold: Int` (default 3) to the `Podcast` entity, DTOs, request/response mappers, and Spring Data JDBC JSON converter.
- Extend Stage 1 of the LLM pipeline (`ArticleScoreSummarizer`) so the structured response includes a `subtopic` field. The prompt receives the podcast's subtopic list; the LLM picks one or returns `null` for on-topic-but-uncategorized articles.
- Persist `subtopic: String?` per article (new nullable column on `articles`).
- Update the briefing/dialogue/interview composers to group articles by subtopic, allocate script time proportional to weight, and emit an explicit rapid-fire segment for subtopics whose weight is at or below `rapidFireWeightThreshold`. Uncategorized articles (`subtopic = null`) go into a synthetic "Other" bucket with implicit weight 1.
- When a podcast has an empty subtopics map, the pipeline behaves exactly as today (backwards compatible).
- Add a "Subtopics" UI section in the podcast settings page with a key/value editor (name → weight 1-10) and an int input for `rapidFireWeightThreshold`.

## Capabilities

### New Capabilities
- `weighted-subtopics`: Per-podcast list of weighted subtopics that drives subtopic classification at scoring time and editorial pacing (full segment vs rapid-fire) at composition time.

### Modified Capabilities
- `podcast-customization`: Adds `subtopics` and `rapidFireWeightThreshold` as configurable per-podcast fields with validation and JSON persistence.
- `llm-processing`: Stage 1 structured response gains a `subtopic` field; the prompt is extended to include the podcast's subtopic list; the persisted article row stores the chosen subtopic.

## Impact

- **Database:** new `subtopic TEXT` column on `articles`; new `subtopics TEXT` (JSON) and `rapid_fire_weight_threshold INTEGER` columns on `podcasts`. Single Flyway migration.
- **Backend:** `Podcast`, `Article`, `PodcastDtos`, `PodcastMappers`, `PodcastService`, `ArticleScoreSummarizer`, all three composers (`BriefingComposer`, `DialogueComposer`, `InterviewComposer`), and the JSON converter for `subtopics`.
- **Frontend:** new "Subtopics" group in the Content tab of `podcasts/[podcastId]/settings/page.tsx`. Reuses the existing `KeyValueEditor` (string→int variant required).
- **No external API impact.** Feature opt-in via the subtopics map; existing podcasts keep current behavior with no migration of user data needed.
- **Cost gate:** unchanged. Subtopic classification rides on the same Stage 1 call — no extra LLM round-trips.
