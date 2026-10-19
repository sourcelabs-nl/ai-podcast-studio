# Tasks

## 1. Scoring

- [x] 1.1 Add migration `V86__add_article_development_date.sql` (nullable TEXT) and `Article.developmentDate`. Verify with the migration test pattern used for V71 and `mvn test`.
- [x] 1.2 Add `developmentDate` to the scoring prompt schema and result, parse to ISO date or null, store on the article. Verify with `ArticleScoreSummarizer` tests: valid date stored, invalid dropped, prompt contains the field.

## 2. Eligibility

- [x] 2.1 Drop `DEVELOPMENT` articles whose development date is more than one day before the window start, logging title, URL and date. Verify with `ArticleEligibilityService` tests for the four spec scenarios.
- [x] 2.2 `mvn test` passes.

## 3. Live

- [x] 3.1 Restart, rescore the articles in episode 296's window, discard and rerun 296; confirm in the log that the republished Developers Digest developments are dropped and the new episode does not open with them. Record the result in `knowledge/`.
