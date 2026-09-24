## 1. Shared JSON mapper

- [x] 1.1 Convert `TopicOrderExtractor` and `CoveredTopicsExtractor` to `@Component` classes injecting `JsonMapper`
- [x] 1.2 Inject the extractors into `BriefingComposer`, `DialogueComposer`, `InterviewComposer` and `EpisodeRecapGenerator`; pass `TopicOrderExtractor` into `RoleTagValidationAdvisor`
- [x] 1.3 Pass the injected `JsonMapper` to the JSON converters in `SqliteDialectConfig`
- [x] 1.4 Update the `spring-ai` skill note about the extractor mapper

## 2. Review cleanups

- [x] 2.1 Remove the no-op `id != null` filter in `LlmPipeline`
- [x] 2.2 Return `LastSeenCursor` from `TwitterFetcher.parseLastSeenId`

## 3. Verification

- [x] 3.1 Update affected tests; `mvn test` passes (1790 tests)
- [x] 3.2 Restart the app and read podcasts with JSON-backed columns through the API
- [x] 3.3 Re-run the code review on the changed files
