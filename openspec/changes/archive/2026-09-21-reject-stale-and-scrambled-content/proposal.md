## Why

Episode 222 shipped with two segments that were not news and a back half in which the wrong
speaker said everything. Four independent faults produced it, and each one was invisible to
everything downstream of it.

**An evergreen page was ingested as news.** The openspec.dev landing page was fetched, scored 7,
and composed into a turn that asked "What's new with OpenSpec?" and answered with a star count, a
monthly-developer figure and a list of compatible editors. The page reports no event, so there was
nothing to answer with. Its `published_at` is the moment someone submitted the link, which places
it inside every window it is offered to and makes it indistinguishable from an announcement.

**A retrospective was summarised as a fresh release.** The zartbot post analysing the
DeepSeek-V4.1-Flash technical report opens "When DeepSeek-V4.1 Flash was released, I thought it
might just be a post-training iteration... but after using it for a while". Its stored summary
opens "DeepSeek released DeepSeek-V4.1-Flash", and the script opens "DeepSeek released V four point
one Flash... the one that stopped me in my tracks". The scoring prompt instructed the model to
"Write directly about what happened", which assumes an event exists and discards the content's own
account of when it happened. Compose only reads bodies when a run is under
`app.briefing.full-body-threshold` (5), which a 40-article day never is, so the summary is the only
view downstream has and the loss is unrecoverable.

**Dedup could not see that the topic was already covered.** The release had been covered the
previous day. `episode_articles.topic` held episode 221's label
`DeepSeek v4.1 Flash vs GLM 5.3 Flash comparison`, but the dedup prompt was given article *titles*
only, and the article behind that label was a tweet headlined about the GLM comparison with
"DeepSeek" nowhere in its title. There was nothing to match on, so the cluster came back `NEW`.

**Mismatched speaker closers scrambled the second half.** Three turns were closed wrongly: two with
the other role's closer and one with a misspelled `</epxert>`. `SPEAKER_TURN_PATTERN` is
`<(\w+)>.*?</\1>` with `DOT_MATCHES_ALL`, so a mismatched pair does not fail to match: it runs on
to the next correct closer and swallows every turn in between into one. `RoleTagValidationAdvisor`
reads the role off the opener, finds it valid, and passes. From the third fault onward the expert
asked the questions and the interviewer explained what a KV cache is.

## What Changes

- The scoring stage SHALL classify every article as `DEVELOPMENT`, `RETROSPECTIVE` or `EVERGREEN`,
  persisted as `articles.news_type`, and SHALL write a summary consistent with that classification
  rather than asserting an event the content does not report.
- Article eligibility SHALL drop `EVERGREEN` articles, which carry no datable event. A missing or
  unrecognised classification SHALL be kept, matching how a missing `published_at` is treated.
- The dedup stage SHALL be given the cluster topic labels of recent episodes alongside the
  historical article titles, and SHALL treat a candidate matching a covered topic as a
  `CONTINUATION` even when its article is new and differently headlined.
- `ComposerUtils` SHALL repair a closing speaker tag that does not match the turn it closes,
  treating the opener as authoritative and logging every rewrite.
- `RoleTagValidationAdvisor` SHALL reject a script whose speaker tags do not pair up, validating the
  script as the cleanup will leave it so a repairable fault costs no extra compose call.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `llm-processing`: the scoring stage gains the temporal classification and timing-preserving summaries.
- `article-eligibility`: gains the evergreen filter and exposes covered topics alongside historical articles.
- `article-dedup-filter`: gains topic-label recall.
- `compose-script-cleanup`: gains the mismatched-closer repair and the turn-structure validation.

## Impact

- Backend: new `NewsType.kt` and `EpisodeHistory.kt`; changes to `ArticleScoreSummarizer.kt`,
  `ArticleEligibilityService.kt` (`findHistoricalArticles` becomes `findHistory`),
  `TopicDedupFilter.kt`, `LlmPipeline.kt`, `ComposerUtils.kt`, `RoleTagValidationAdvisor.kt`.
- Schema: `V71__add_article_news_type.sql` adds a nullable `articles.news_type`.
- Tests: 24 new cases across `ScriptCleanupTest`, `RoleTagValidationAdvisorTest`,
  `ArticleEligibilityServiceTest`, `TopicDedupFilterTest` and `ArticleScoreSummarizerTest`.
- No API, frontend or configuration change.
