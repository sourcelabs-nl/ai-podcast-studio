## Why

The episode sources HTML page currently lists all source articles as a flat, unsorted list. For episodes with 30+ sources, this makes it difficult for listeners to find the articles backing a specific topic they heard discussed. The dedup filter already assigns topic labels and the feed generator already groups by topic in RSS descriptions, but the sources HTML page ignores this information entirely.

## What Changes

- The composer (briefing, dialogue, interview) outputs an ordered list of topic labels reflecting the order topics are discussed in the script, returned as part of `CompositionResult`
- `PipelineResult` carries the topic ordering through to `EpisodeService`
- Topic order is stored in `episode_articles.topic_order` so it can be retrieved at HTML generation time
- `EpisodeSourcesGenerator` groups articles by topic with topic headings, ordered by discussion order in the episode
- The sources page heading changes from "Sources" to "Topics Covered in This Episode" (or similar)
- Episodes without topic data (historical or recomposed) fall back to a flat article list
- The recap generator prompt is updated to be aware of topic labels so the summary text naturally references the topics discussed

## Capabilities

### New Capabilities

_(none, this enhances existing capabilities)_

### Modified Capabilities

- `episode-sources-file`: Sources HTML groups articles under topic headings, ordered by discussion order
- `episode-article-tracking`: `episode_articles` gains a `topic_order` column; pipeline stores discussion order per topic
- `episode-show-notes`: Recap generation prompt includes topic labels so the summary naturally references topics covered

## Impact

- **Composer classes** (`BriefingComposer`, `DialogueComposer`, `InterviewComposer`): prompt change to request topic ordering, `CompositionResult` gains `topicOrder` field
- **`LlmPipeline`**: passes topic ordering from composition through to `PipelineResult`
- **`EpisodeService`**: stores `topic_order` when saving episode-article links
- **`EpisodeArticle`** entity and repository: new nullable `topic_order` column
- **`EpisodeSourcesGenerator`**: rewritten to group articles by topic with ordered headings
- **`EpisodeRecapGenerator`**: prompt updated to include topic labels
- **Database migration**: adds `topic_order` INTEGER column to `episode_articles`
- **Existing episodes**: unaffected (null `topic_order` triggers flat-list fallback)
