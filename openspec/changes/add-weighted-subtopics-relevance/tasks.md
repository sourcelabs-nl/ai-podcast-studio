## 1. Database migration

- [x] 1.1 Write `V<next>__add_weighted_subtopics.sql` adding `subtopics TEXT` and `rapid_fire_weight_threshold INTEGER NOT NULL DEFAULT 3` to `podcasts`, and `subtopic TEXT` to `articles`
- [x] 1.2 Verify the migration applies cleanly against a copy of `./data/ai-summary-podcast.db`

## 2. Domain model + persistence

- [x] 2.1 Add `subtopics: Map<String, Int>?` and `rapidFireWeightThreshold: Int` (default 3) to `Podcast` entity
- [x] 2.2 Add a JSON converter for `subtopics` following the existing `llmModels`/`ttsVoices` pattern
- [x] 2.3 Add `subtopic: String?` to `Article` entity and the repository row mapper
- [x] 2.4 Update Spring Data JDBC tests covering the new columns (round-trip read/write)

## 3. DTOs + validation + REST surface

- [x] 3.1 Add `subtopics` and `rapidFireWeightThreshold` to `PodcastDtos` (create, update, response)
- [x] 3.2 Add `PodcastMappers` rows for both fields including clearing-via-empty-map semantics
- [x] 3.3 Add validation: weights in 1-10, names non-empty, threshold in 0-10. Reject with HTTP 400 and a clear error.
- [x] 3.4 Add `subtopic` to the episode-article response DTO + mapper
- [x] 3.5 Controller / service unit tests for the new fields and validation paths

## 4. Scoring stage (Stage 1)

- [x] 4.1 Extend `ScoreSummarizeResult` with `subtopic: String? = null`
- [x] 4.2 Update `ArticleScoreSummarizer.buildPrompt` to include the subtopic list when `podcast.subtopics` is non-empty, and to instruct the LLM to pick one verbatim or return null
- [x] 4.3 Update the response schema description in the prompt (`{relevanceScore, subtopic, summary}` vs legacy `{relevanceScore, summary}`)
- [x] 4.4 Normalize the LLM-returned `subtopic` against the configured names (case-insensitive); unknown → null
- [x] 4.5 Persist `subtopic` on the article alongside `relevanceScore` and `summary`
- [x] 4.6 Unit tests: prompt-shape (subtopics included only when configured), JSON parsing, unknown-name normalization, null-on-disabled

## 5. Composer changes

- [x] 5.1 Add `app.compose.rapid-fire-budget-fraction` (default 0.15) to `AppProperties` and `application.yaml`
- [x] 5.2 Introduce a `SubtopicPlan` data class capturing per-subtopic word budgets and the rapid-fire bucket, computed from `podcast.subtopics`, `podcast.rapidFireWeightThreshold`, and `targetWords`
- [x] 5.3 Extend `BriefingComposer` to build the `SubtopicPlan` when subtopics are configured and emit a prompt that asks for a labeled "And in brief:" rapid-fire segment after the full segments
- [x] 5.4 Extend `DialogueComposer` with style-appropriate rapid-fire phrasing ("Quick hits before we wrap")
- [x] 5.5 Extend `InterviewComposer` with style-appropriate rapid-fire phrasing ("Lightning round to close")
- [x] 5.6 Backwards-compat path: when `podcast.subtopics` is empty/null, composer prompts are byte-identical to the pre-feature behavior
- [x] 5.7 Deep-dive interaction: when `deepDiveEnabled = true` AND subtopics is non-empty, augment the webSearch instruction block to (a) restrict invocations to full-segment articles only, (b) bias toward higher-weight subtopics with newsworthiness as tiebreaker, (c) keep the 3-call episode-wide cap. When subtopics is empty, use the existing deep-dive prompt verbatim.
- [x] 5.8 Composer unit tests: full-only, mixed, uncategorized fallback to "Other" bucket, full-tier-empty → flat layout, backwards-compat byte-identical prompt, deep-dive-on + subtopics-on prompt contains the full-segment-only webSearch instruction, deep-dive-on + subtopics-off prompt matches today's deep-dive prompt verbatim

## 6. Frontend

- [x] 6.1 Add `subtopics?: Record<string, number>` and `rapidFireWeightThreshold?: number` to the `Podcast` type in `frontend/src/lib/types.ts`
- [x] 6.2 Add a "Subtopics" group in the Content tab of `podcasts/[podcastId]/settings/page.tsx` with a string→int key/value editor (rejecting weights outside 1-10) and helper text on what weights control
- [x] 6.3 Add a `rapidFireWeightThreshold` number input next to it (0-10, default 3) with helper text
- [x] 6.4 Include both fields in the save payload
- [x] 6.5 Show the article's `subtopic` value in the episode articles tab (small badge or text column)

## 7. Verification

- [x] 7.1 Run `mvn test` and ensure all tests pass
- [ ] 7.2 Restart the app via `./stop.sh && ./start.sh`
- [ ] 7.3 Live run: configure 3-4 subtopics on a podcast, trigger generation; confirm articles persist a subtopic, the script contains an explicit "And in brief" segment for low-weight subtopics, and high-weight subtopics get visibly more script time
- [ ] 7.4 Confirm backwards compatibility: a second podcast with no subtopics produces a script that matches the pre-feature output structure (no rapid-fire segment, no subtopic-shaped prompt)
- [ ] 7.4a Confirm the full-tier-empty fallback: configure subtopics that the day's news doesn't match (or all-low-weight), regenerate, and confirm the script falls back to flat layout with no rapid-fire label
- [ ] 7.5 Confirm validation: API rejects weight 0, weight 11, threshold -1, threshold 11
- [x] 7.6 Run `openspec validate add-weighted-subtopics-relevance --strict`
- [x] 7.7 Update `README.md` to document the subtopics feature and the rapid-fire threshold
