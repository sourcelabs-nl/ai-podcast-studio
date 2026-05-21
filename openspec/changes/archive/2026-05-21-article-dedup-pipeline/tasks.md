## 1. Article Eligibility Service

- [x] 1.1 Create `ArticleEligibilityService` with `findEligibleArticles(podcast)` method — applies `isProcessed` check and article age gate (exclude articles published before latest GENERATED+published episode's `generated_at`)
- [x] 1.2 Add `canResetArticle(articleId)` method — returns false if article is linked to any GENERATED episode with PUBLISHED publications
- [x] 1.3 Add repository query to find the latest GENERATED episode with PUBLISHED publications for a podcast (join `episodes` → `episode_publications`)
- [x] 1.4 Write tests for `ArticleEligibilityService` — age gate filtering, reset guard with published/unpublished episodes, edge case with no published episodes

## 2. Discard Reset Guard

- [x] 2.1 Update `EpisodeService.discardAndResetArticles()` to delegate reset decision to `ArticleEligibilityService.canResetArticle()` — skip reset/deletion for articles linked to published episodes
- [x] 2.2 Write tests for updated discard logic — article linked to published episode not reset, article only linked to discarded episode is reset, aggregated article linked to published episode not deleted

## 3. Topic Dedup Filter

- [x] 3.1 Create `TopicDedupFilter` component with structured input/output types (`DedupCandidate`, `DedupCluster`, `DedupResult`)
- [x] 3.2 Implement LLM prompt for clustering — accepts candidate article titles+summaries and historical article titles+summaries, returns structured JSON with clusters
- [x] 3.3 Add repository query to fetch historical articles from recent GENERATED episodes (join `episode_articles` → `articles` → `episodes` where status = GENERATED, limited by lookback window)
- [x] 3.4 Implement cluster output transformation — map `selectedArticleIds` back to full articles, prepend `[FOLLOW-UP: ...]` headers for CONTINUATION clusters
- [x] 3.5 Write tests for `TopicDedupFilter` — NEW cluster passes through, CONTINUATION with new info keeps articles, CONTINUATION without new info skips, cross-source dedup merges clusters, single-article clusters untouched

## 4. Pipeline Integration

- [x] 4.1 Update `LlmPipeline.run()` to delegate article selection to `ArticleEligibilityService` instead of querying repository directly
- [x] 4.2 Add dedup filter step between scoring and composition in `LlmPipeline.run()` — pass eligible articles + historical articles to `TopicDedupFilter`, use filtered output for composition
- [x] 4.3 Update `LlmPipeline.preview()` to use `ArticleEligibilityService` and `TopicDedupFilter`
- [x] 4.4 Update `LlmPipeline.recompose()` to use `ArticleEligibilityService` for any eligibility checks
- [x] 4.5 Remove `fetchRecentRecaps()` from `LlmPipeline` — no longer needed for composer
- [x] 4.6 Update `LlmPipelineTest` — verify dedup filter is called, verify article eligibility delegation, verify recap no longer passed to composer

## 5. Composer Changes

- [x] 5.1 Remove `previousEpisodeRecaps` parameter from `BriefingComposer.compose()` — remove recapBlock from prompt
- [x] 5.2 Remove `previousEpisodeRecaps` parameter from `DialogueComposer.compose()` — remove recapBlock from prompt
- [x] 5.3 Remove `previousEpisodeRecaps` parameter from `InterviewComposer.compose()` — remove recapBlock from prompt
- [x] 5.4 Add support for `[FOLLOW-UP: ...]` headers in article blocks passed to composers — these come pre-formatted from the dedup filter transformation
- [x] 5.5 Update `BriefingComposerTest`, `DialogueComposerTest`, `InterviewComposerTest` — remove recap-related tests, add tests verifying follow-up annotations appear in prompt

## 6. Cleanup

- [x] 6.1 Remove `recapLookbackEpisodes` usage from composer/pipeline code (repurpose config for dedup filter lookback)
- [x] 6.2 Verify `EpisodeRecapGenerator` still runs after episode creation (unchanged — recaps for publication only)
- [x] 6.3 Run full test suite and fix any broken tests
