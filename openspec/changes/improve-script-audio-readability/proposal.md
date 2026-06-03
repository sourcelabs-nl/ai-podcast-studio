## Why

Recent episodes pack stories that are dense with benchmark scores, percentages, and parameter counts. These read fine on a page but "dazzle" a listener when spoken aloud, turning into an unfollowable wall of digits. Two related gaps make this worse: the rapid-fire segment audibly counts off "item one, item two…", and complex topics get rushed past without enough explanation for non-expert listeners. Separately, some RSS feeds only provide a short summary, so the pipeline never sees the full article context that would let it explain a topic well.

## What Changes

- Add a shared "numbers for the ear" rule to all compose-stage prompts (briefing, dialogue, interview): round to clean values, lead with what a number means, voice at most one number per spoken claim, and de-emphasize benchmark proper-names.
- Add a shared "explain for non-experts" rule encouraging brief, plain-language elaboration (what it is, how it works, consequences) on complex or unfamiliar topics, using the web-search tool for outside context when available.
- Stop the rapid-fire segment from enumerating items aloud (no "item one/two", no "first, second, third"); introduce it naturally and flow between items. Cap each rapid-fire item to one rounded number with no benchmark name+score pair.
- Deep-fetch the full article behind each RSS entry's link and prefer its extracted text over the (often summary-only) feed body, with graceful fallback and host skip rules.

## Capabilities

### New Capabilities
- `script-audio-readability`: Audio-oriented composition rules shared across all podcast styles — how numbers/statistics are verbalized and when topics get extra plain-language explanation for non-expert listeners.
- `article-deep-fetch`: Retrieving the full article body from an RSS entry's link when the feed carries only a summary, so downstream scoring and composition see full context.

### Modified Capabilities
- `weighted-subtopics`: The rapid-fire tier must be introduced and delivered conversationally without announcing ordinal numbers or counting items aloud, and each item voices at most one rounded number.

## Impact

- Composer prompts: `ComposerUtils.kt` (new shared blocks), `BriefingComposer.kt`, `DialogueComposer.kt`, `InterviewComposer.kt`, `SubtopicPlan.kt`.
- Source ingestion: new `ArticleContentFetcher.kt`, modified `RssFeedFetcher.kt` and `SourcePoller.kt` (YouTube opt-out), new `app.source.deep-fetch` config in `AppProperties.kt`.
- No database, API, or schema changes. No breaking changes.
