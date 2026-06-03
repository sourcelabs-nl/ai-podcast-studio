## 1. Shared composition rules

- [x] 1.1 Add `buildNumbersBlock()` to `ComposerUtils.kt`
- [x] 1.2 Add `buildAudienceBlock()` to `ComposerUtils.kt`
- [x] 1.3 Inject both blocks into `BriefingComposer`, `DialogueComposer`, `InterviewComposer`
- [x] 1.4 Add prompt-assertion tests for both rules in each composer test

## 2. Rapid-fire delivery

- [x] 2.1 Append "no counting aloud" and "one rounded number per item" rules to the rapid-fire instruction in `SubtopicPlan.kt`
- [x] 2.2 Add `SubtopicPlanTest` assertions for the new rapid-fire rules

## 3. Article deep-fetch

- [x] 3.1 Add `DeepFetchProperties` and `source.deepFetch` to `AppProperties.kt`
- [x] 3.2 Create `ArticleContentFetcher` reusing `ContentExtractor`
- [x] 3.3 Update `RssFeedFetcher` to deep-fetch, prefer richer text, skip hosts, fall back on error
- [x] 3.4 Disable deep-fetch for YouTube polling in `SourcePoller`
- [x] 3.5 Add `ArticleContentFetcherTest` and deep-fetch cases in `RssFeedFetcherTest`

## 4. Verification

- [x] 4.1 Run full test suite (`mvn test`) — all green
- [x] 4.2 Restart the app so the running instance picks up the changes
