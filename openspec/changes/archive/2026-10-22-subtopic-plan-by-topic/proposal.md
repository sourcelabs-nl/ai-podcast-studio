# Proposal

## Why

In episode 303 (2026-10-22) the dedup stage grouped articles into topic clusters correctly (for example "OpenAI Patch the Planet PHP vulnerability discovery" with 3 articles, "Claude Duet proactive agent" with 3), but `SubtopicPlan` buckets individual articles by their own subtopic and lists rapid-fire items per article. All three PHP articles were classified "Agent research" (weight 3, rapid-fire), so the lead story came back as three separate rapid-fire items after the model had opened with it. Claude Duet was split between rapid-fire (Agent research) and a full segment (Model releases). 4 of the 6 rapid-fire items repeated stories already told.

## What Changes

- When topic assignments are available, the plan assigns each topic cluster as a whole to one bucket: the highest-weight subtopic among its articles (ties resolved by the bucket name order already used).
- The rapid-fire tier lists one item per topic, labelled with the topic label, ranked and capped as before.
- The rapid-fire prompt block tells the model to skip a rapid-fire item already covered earlier in the episode.
- `ComposeContext` carries an article-id to topic map (like `followUpAnnotations`). The pipeline fills it from the dedup results, and on retry or recompose from the stored `episode_articles` topics.
- Without topic assignments (empty map) the plan keeps the per-article behaviour.

## Capabilities

### New Capabilities

### Modified Capabilities
- `weighted-subtopics`: "Rapid-fire item cap and ranking per podcast" and "Subtopic-aware pacing in composer" (topic-level bucketing and rapid-fire items).

## Impact

- `SubtopicPlan`, `ComposeContext`, `LlmPipeline` / `PipelineRunner` (topic map wiring), no schema or API change.
- Tests: `SubtopicPlanTest` (reproduction with episode 303 data), pipeline test for the topic map.
