# Tasks

## 1. Scoring prompt

- [x] 1.1 Add the listener-interest rubric to the scoring prompt in `ArticleScoreSummarizer`. Verify with `ArticleScoreSummarizerTest` `prompt scores relevance on topic fit and listener interest together`.
- [x] 1.2 Add the release floor of 7 and the paper cap of 6. Verify with `ArticleScoreSummarizerTest` `prompt floors usable releases at 7 and caps incremental papers at 6`.

## 2. Threshold

- [x] 2.1 Set the relevance threshold of "The Daily Agentic AI Podcast" to 7 in the UI (per-podcast setting, no code).

## 3. Verification

- [x] 3.1 `mvn test` passes.
