## Why

A code review of `src/main/kotlin` found classes that construct their own Jackson mapper with `jacksonObjectMapper()`, which the project rules forbid (SB6: inject the Spring-managed `JsonMapper`). Settings configured under `spring.jackson.*` in `application.yaml` do not reach a hand-built mapper, so those classes parse and write JSON differently from the rest of the application. The same review found a defensive null-ID filter on persisted articles (DB4) and a `Pair` return type (A6).

## What Changes

- `TopicOrderExtractor` and `CoveredTopicsExtractor` change from Kotlin `object`s to `@Component` classes that inject `JsonMapper`. Their callers (`BriefingComposer`, `DialogueComposer`, `InterviewComposer`, `EpisodeRecapGenerator`) inject them, and `RoleTagValidationAdvisor` receives the `TopicOrderExtractor` through its constructor.
- The JSON-backed converters in `SqliteDialectConfig` (map, LLM model overrides, subtopics) receive the injected `JsonMapper` from the `jdbcCustomConversions` bean method.
- `LlmPipeline` drops `eligible.filter { it.id != null }`: eligible articles come from the repository and always carry an ID.
- `TwitterFetcher.parseLastSeenId` returns a `LastSeenCursor(xUserId, sinceId)` data class instead of `Pair<String?, String?>`.
- The `spring-ai` skill no longer lists `CoveredTopicsExtractor` as a known gap.

No behaviour change: the only central Jackson setting (`allow-backslash-escaping-any-character`) makes reads more lenient, so JSON already stored in the database still reads.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. This is an internal refactor with no requirement change (`skip_specs: true`).

## Impact

- Main: `llm/TopicOrderExtractor.kt`, `llm/CoveredTopicsExtractor.kt`, `llm/BriefingComposer.kt`, `llm/DialogueComposer.kt`, `llm/InterviewComposer.kt`, `llm/EpisodeRecapGenerator.kt`, `llm/RoleTagValidationAdvisor.kt`, `llm/LlmPipeline.kt`, `source/TwitterFetcher.kt`, `config/SqliteDialectConfig.kt`.
- Tests: the extractor, composer, recap generator and role-tag advisor tests construct the new dependencies.
- No API, schema or dependency changes.
