## Why

Episode 197 was regenerated as 198 and buried the day's biggest story. GPT-6 Astra launched on 3 September; 197 opened with it ("No prizes for guessing which story leads today"), while 198, recomposed from the *same 40 articles*, opened on a side angle and said:

> since we covered the GPT six Astra launch itself yesterday, today I want to focus on what's changed since

That is false. Yesterday's episode 196 mentions Astra **zero** times. Episode 195 (2 September) mentioned it seven times, but that was the pre-release exploit-benchmark story, not the launch.

The cause is a gap in the regeneration path. Dedup computes, per cluster, whether a topic is `NEW` or a `CONTINUATION` and what the previous coverage was, and the normal compose path turns that into `[FOLLOW-UP: ...]` headers above the affected article group. But `episode_articles` persists only `topic` and `topic_order`; the cluster status and its `previousContext` are stored nowhere. So `LlmPipeline.recompose` calls the composer with no `followUpAnnotations` at all, and a regenerated episode has **no** continuity signal.

With nothing authoritative to go on, the composer falls back on the `searchPastEpisodes` tool, and the prompt tells it to:

> HISTORY CHECK: Before treating any subject as new, call the `searchPastEpisodes` tool [...] If the tool returns a prior episode that covered the topic, either skip it, treat it as a follow-up referencing the prior coverage, or angle the segment as an update.

The log shows `searchPastEpisodes query='GPT-6 Astra' matches=3`, and the composer did exactly as instructed. But a keyword hit on a product name is not evidence that today's development was already covered, and that instruction outranked FRONT-LOAD THE BEST STORY. The tool's fuzzy recall was allowed to overrule dedup's title-and-summary comparison against the actual historical article set.

## What Changes

- `episode_articles` gains a `follow_up_context TEXT` column, persisted wherever an episode-article link is written, so a regeneration can reproduce the annotations the original compose received. The column is the natural companion to `topic` and `topic_order`, which are already persisted for exactly this reason.
- `LinkedArticlesResult` and `PipelineResult` carry the annotations, so `recompose` receives them and a regenerated episode re-persists them, keeping a regeneration of a regeneration faithful too.
- The HISTORY CHECK prompt block is re-scoped. The dedup annotations become authoritative for what is new and what is a continuation. The tool remains available for referencing prior coverage accurately and avoiding repeated phrasing, but a keyword match alone may no longer demote or skip a story, and an article carrying no `[FOLLOW-UP: ...]` header is to be treated as new. This half also fixes the live symptom: today's episode 197 has no stored annotations, so a regeneration of it cannot recover them, and only the prompt guard stops the composer inventing prior coverage.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `episode-regeneration`: a regeneration SHALL recompose with the source episode's follow-up annotations, not without them.
- `article-dedup-filter`: the annotations it produces SHALL be persisted, and SHALL outrank the history-lookup tool when the two disagree.

## Impact

- Schema: migration `V66` adds `episode_articles.follow_up_context`. Nullable, so existing rows (including today's 197) read back as no annotation, which is the current behaviour.
- Backend: `EpisodeArticle`, `EpisodeArticleRepository.insertIgnore`, `EpisodeService.saveDedupResults`, `EpisodeService.saveEpisodeArticleLinks`, `EpisodeService.findLinkedArticlesAndTopics`, `LinkedArticlesResult`, `PipelineResult`, `LlmPipeline.compose`/`recompose`, `PodcastService.runRegeneration`, `ComposerUtils.buildHistoryLookupBlock`.
- No API or frontend change.
