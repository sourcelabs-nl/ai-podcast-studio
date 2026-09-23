## 1. Schema

- [x] 1.1 Add migration `V79__add_focus_episode_fields.sql`: `episodes.focus TEXT NULL`, `episodes.review_feedback TEXT NULL`, and table `episode_research_sources (id INTEGER PRIMARY KEY, episode_id INTEGER NOT NULL REFERENCES episodes(id), query TEXT NOT NULL, title TEXT NOT NULL, url TEXT NOT NULL, ordinal INTEGER NOT NULL)`. Verify `mvn test` runs the Flyway migration cleanly against a fresh test database.
- [x] 1.2 Add `focus: String? = null` and `reviewFeedback: String? = null` to `Episode` (`store/Episode.kt`), and an `EpisodeResearchSource` entity + `EpisodeResearchSourceRepository` (insert per hit, find by episode id). Verify with a MockK/repository test that a saved source row round-trips.
- [x] 1.3 Expose `focus` on the episode API response (`PodcastMappers.kt`/episode DTO). Verify with a controller test that `GET .../episodes/{id}` includes `focus`.

## 2. Focus-scoped selection and scoring

- [x] 2.1 Add `ArticleEligibilityService.findEligibleArticlesForFocus(sourceIds, podcast, window)` built on `ArticleRepository.findUnprocessedSince` plus the existing window/evergreen filtering. Verify with a unit test that an article below the podcast's general relevance threshold is still returned when it falls in the window.
- [x] 2.2 Add `ArticleScoreSummarizer.scoreForFocus(articles, focus, podcast, filterModelDef)` that reuses `buildPrompt`'s structure with a `topicOfInterest` override and returns results without calling `articleRepository.save()`. Verify with a MockK test that the podcast's `topic` field is never read for the prompt's "Topic of interest" line and that `articles` table rows are unchanged after the call.
- [x] 2.3 Wire a focus pipeline path in `LlmPipeline`/`PodcastService` that: resolves the window via `resolveForNow`, selects via 2.1, scores via 2.2, filters by `podcast.relevanceThreshold`, and fails with a message naming the focus text when nothing clears the threshold. Verify with a unit test for both the "some relevant" and "none relevant" cases.

## 3. Episode creation without consumption or schedule advance

- [x] 3.1 Add an `isFocusEpisode` parameter (derived from `episode.focus != null`) to `EpisodeService.createEpisodeFromPipelineResult()` that skips `markArticlesAsProcessed()` and the `lastGeneratedAt` update, while still calling `saveEpisodeArticleLinks()`. Verify with a MockK test that a focus episode's articles remain `is_processed = false` and the podcast's `lastGeneratedAt` is unchanged.
- [x] 3.2 Force `status = PENDING_REVIEW` for a focus episode regardless of `podcast.requireReview`. Verify with a test that a focus episode on a `requireReview = false` podcast still lands in `PENDING_REVIEW`.
- [x] 3.3 Verify with an integration-style test that a regular episode generated after a focus episode still selects the focus episode's unconsumed articles when they remain eligible.

## 4. Research: forced-on, higher budget, recorded sources

- [x] 4.1 Add `FOCUS_RESEARCH_TOOL_CAP = 5` alongside `RESEARCH_TOOL_CAP = 3` in `research/ResearchTool.kt`. Update `ChatClientFactory.buildComposeTools()` to register `ResearchTool` when `podcast.deepDiveEnabled || episode?.focus != null`, with the cap chosen accordingly. Verify with a test on `buildComposeTools` that a focus episode registers the tool with a 5-call budget even when `deepDiveEnabled = false`.
- [x] 4.2 Add `focusResearchEnabled: Boolean = false` to `ComposeContext`; update `buildWebSearchBlock` call sites in `BriefingComposer`, `DialogueComposer`, `InterviewComposer` to pass `podcast.deepDiveEnabled || context.focusResearchEnabled`. Verify with a test that a focus episode's compose prompt mentions `webSearch` even when `deepDiveEnabled = false`.
- [x] 4.3 Persist each `webSearch` hit to `episode_research_sources` from `ResearchTool.webSearch()` when composing for an episode id. Verify with a MockK test that a search returning 3 hits creates 3 rows with the query and each hit's title/url.
- [x] 4.4 Expose recorded research sources for an episode via `EpisodeService`/`EpisodeController` (e.g. included in the episode detail response or a dedicated endpoint). Verify with a controller test.

## 5. Review: display and feedback-driven recompose

- [x] 5.1 Add `PodcastService.recomposeFocusEpisodeAsync(episode, podcast, feedback)` that recomposes (and reruns research) against `EpisodeService.findLinkedArticlesAndTopics(episode.id)`, updates the same episode's script via `EpisodeService.updateScript()`, saves `feedback` to `review_feedback`, and leaves status at `PENDING_REVIEW`. Verify with a MockK test that the episode row id is unchanged and `review_feedback` reflects the latest submission across two consecutive calls.
- [x] 5.2 Add `extraInstruction: String? = null` to `ComposeContext` and inject it into the compose prompts (monologue/dialogue/interview) the same way `ttsScriptGuidelines` is injected. Verify with a prompt-building unit test that the feedback text appears in the built prompt.
- [x] 5.3 Add `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/regenerate-script` (or equivalent) in `EpisodeController` accepting `{ feedback: String }`, valid only for a focus episode in `PENDING_REVIEW`, calling 5.1. Verify with a controller test for success, wrong-status (409), and non-focus-episode rejection.
- [x] 5.4 Frontend: on the episode detail/review page, for a focus episode show the selected articles, the recorded research sources, and an estimated spoken length computed from the script word count using the page's existing `WORDS_PER_MINUTE` figure; add a feedback textarea and "Regenerate script" button calling 5.3. Verify manually (see task 8) and, where the page has existing component tests, add one covering the new elements' presence for a focus episode.

## 6. Continuity nudge for the next regular episode

- [x] 6.1 Extend `EpisodeHistoryRepository.search()` and `HistoryLookupTool.searchPastEpisodes()`/`PastEpisodeMatchDto` to include `isFocusEpisode` (from `episodes.focus IS NOT NULL`). Verify with a repository test that a focus episode's match has `isFocusEpisode = true` and a regular episode's has `false`.
- [x] 6.2 Update the compose-stage history-check instructions (shared prompt text used by `BriefingComposer`/`DialogueComposer`/`InterviewComposer`) so that a match with `isFocusEpisode = true` is treated as a continuation to build on with new developments, with limited reuse of the same articles allowed, rather than a topic to skip. Verify with a prompt-building unit test asserting the new clause is present.
- [x] 6.3 Give a regular episode's compose prompt the focus episodes generated since the previous regular episode (focus text and date), via a new `recentFocusEpisodes` on `ComposeContext`, with the instruction to look them up with `searchPastEpisodes` and treat the topic as a follow-up. Verify with a prompt-building test that the focus text appears, and a service test that only focus episodes after the last regular episode are passed.

## 7. Frontend: Focus field on the upcoming page

- [x] 7.1 Add a "Focus" text input next to "Generate Episode" on `frontend/src/app/podcasts/[podcastId]/upcoming/page.tsx`; include `focus` in the `POST /generate` body only when non-empty. Verify with `npx tsc --noEmit` and a manual check (task 8) that generating with the field empty sends the same request as before.
- [x] 7.2 Update `POST /generate` on the backend (`PodcastController.generate`) to accept an optional `focus` field in the request body and route to the focus pipeline path (section 2-3) when present and non-blank. Verify with a controller test for both branches.
- [x] 7.3 Put "Generate Episode" behind a confirmation dialog (the page's existing `AlertDialog`): it states whether a regular or a focus episode will be generated (showing the focus text) and how many upcoming articles will be considered, with Cancel and Generate buttons; nothing is POSTed until Generate is confirmed. Verify with `npx tsc --noEmit` and the manual check in task 8.

## 8. Full verification

- [x] 8.1 Run `mvn test` and confirm all tests pass, including any existing tests whose constructors/signatures changed because of `ComposeContext`, `ChatClientFactory.buildComposeTools`, or `EpisodeService.createEpisodeFromPipelineResult` changes.
- [x] 8.2 Restart the app (`./stop.sh` then `./start.sh`).
- [x] 8.3 Live check: generate a focus episode for focus "Claude Opus 5.5 release" via the API (or the upcoming-page Focus field), confirm it reaches `PENDING_REVIEW` with selected articles and recorded research sources visible on review, submit one feedback-driven "Regenerate script" and confirm the script changes while the same episode id and articles are retained, then approve and confirm TTS starts.
- [x] 8.4 Confirm the podcast's `lastGeneratedAt` and the next regular episode's window were unaffected by the focus episode generated in 8.3.

## 9. Discard rollback

- [x] 9.1 Roll `lastGeneratedAt` back on discard to the latest regular episode that still covers its window (`EpisodeRepository.findLatestCoveringByPodcastId`: not failed, not discarded, not a focus episode, published or not) instead of the latest published one, so discarding an episode after an unpublished regular one does not make the scheduler regenerate an already covered slot. Verified by `EpisodeServiceTest` discard rollback tests.

## 10. Announcing the extra episode and length feedback

- [x] 10.1 The focus compose prompt says the episode is an extra, special episode on top of the regular ones, in both the introduction and the closing (where it says the regular episode follows as usual). Verified by `InterviewComposerTest`.
- [x] 10.2 A focus episode's show notes open with `Special episode: <focus>.` before the recap, both at generation and in `regenerateAllShowNotes`. Verified by `ShowNotesTest`.
- [x] 10.3 Reviewer feedback that asks for a different length overrides the podcast's target word count. Verified by `InterviewComposerTest`.
