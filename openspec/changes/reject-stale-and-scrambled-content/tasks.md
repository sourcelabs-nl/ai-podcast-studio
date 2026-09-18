<!-- Implemented before this change was written; every task below is already done. -->

## 1. Place articles in time at scoring

- [x] 1.1 Add `NewsType` (`DEVELOPMENT`, `RETROSPECTIVE`, `EVERGREEN`) with a lenient `parse`
- [x] 1.2 Add `news_type` to `articles` (`V71__add_article_news_type.sql`) and to the `Article` entity
- [x] 1.3 Add `newsType` to `ScoreSummarizeResult`, persist it, and name it in the scoring log line
- [x] 1.4 Ask for the classification in the scoring prompt and define all three values
- [x] 1.5 Replace the blanket "write about what happened" rule with per-type summary rules, so a
      retrospective is not summarised as a fresh release

## 2. Keep evergreen pages out of episodes

- [x] 2.1 Drop `EVERGREEN` articles in `ArticleEligibilityService.findEligibleArticles`
- [x] 2.2 Log every dropped article with its title and URL
- [x] 2.3 Keep an article whose classification is null or unrecognised

## 3. Give dedup topic-level recall

- [x] 3.1 Add `EpisodeHistory(articles, coveredTopics)`
- [x] 3.2 Replace `findHistoricalArticles` with `findHistory`, collecting topics in the same pass
- [x] 3.3 Send a "Topics already covered in recent episodes" block in the dedup prompt
- [x] 3.4 Add the rules that a covered topic is a `CONTINUATION` even with a new article, and that a
      fresh analysis of a covered release is not a new release
- [x] 3.5 Update `LlmPipeline` to pass the history object

## 4. Stop mismatched speaker tags from scrambling an episode

- [x] 4.1 Add `repairMismatchedTurnClosers`, treating the opener as authoritative, logging each rewrite
- [x] 4.2 Run it in `cleanUpComposedScript` after the square-bracket fix and before the unclosed-final fix
- [x] 4.3 Add `findTurnStructureProblem` reporting the first pairing fault
- [x] 4.4 Validate the cleaned script in `RoleTagValidationAdvisor` so a repairable fault costs no retry

## 5. Tests

- [x] 5.1 `ScriptCleanupTest`: 12 cases for the repair and the structure check
- [x] 5.2 `RoleTagValidationAdvisorTest`: repairable fault does not retry; unrepairable retries then fails
- [x] 5.3 `ArticleEligibilityServiceTest`: evergreen dropped, unknown kept, topics returned
- [x] 5.4 `TopicDedupFilterTest`: covered-topics block present, absent, and the continuation rule
- [x] 5.5 `ArticleScoreSummarizerTest`: prompt asks for the classification; `NewsType.parse` cases
- [x] 5.6 `mvn test` green (1551 tests)
