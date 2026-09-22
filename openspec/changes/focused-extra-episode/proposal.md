## Why

Regular episodes only ever cover the podcast's standing topic, on the podcast's own schedule. When something specific happens between two regular episodes (a product launch, an announcement) there is no way to get an episode about just that, on demand, without disturbing the regular schedule or losing the articles the regular episode would otherwise use.

## What Changes

- Upcoming page gains an optional "Focus" text field next to "Generate Episode". Filling it and generating starts a focus episode; leaving it empty keeps today's behavior unchanged.
- `POST /generate` accepts an optional `focus` string. `episodes` gains `focus` (nullable) and `review_feedback` (nullable) columns (migration `V79`). `Episode` exposes `focus` in its API response.
- A focus episode selects from the same article window a regular episode would use right now, but scores articles for relevance to the focus text instead of the podcast's topic, and keeps only focus-relevant articles. A run with none is a failure, reported with a clear message.
- A focus episode always runs research (web search), regardless of the podcast's deep-dive setting, at a raised per-episode budget (5 searches instead of the regular 3), and records the research queries and the sources they returned against the episode.
- A focus episode always stops at `PENDING_REVIEW` before TTS, regardless of the podcast's `requireReview` setting. The review screen shows the selected articles, the research sources, and an estimated spoken length. A feedback textbox recomposes the script (and reruns research) against the same locked article set, repeatably, storing the latest feedback text; approving from review starts TTS through the existing approval flow.
- A focus episode does not advance the podcast's regular schedule and does not mark its articles as consumed: the next regular episode can still select them. That next regular episode instead receives the focus episode's recap as continuity context, so it may continue the topic or reuse a limited number of the same articles, but is nudged to bring new developments rather than repeat the focus episode.

## Capabilities

### New Capabilities
- `focus-episode-generation`: focus-scoped article selection (scoring against a free-text focus instead of the podcast topic), the all-or-nothing relevance gate, and the schedule/consumption exemption for focus episodes.

### Modified Capabilities
- `frontend-upcoming-episode`: adds the optional Focus field and wires it into the existing generate action.
- `async-manual-generation`: `POST /generate` accepts the optional `focus` field and starts a focus run when it is present.
- `deep-dive-research`: a focus episode forces research on at a higher budget and its queries/sources are persisted and exposed for review, regardless of the podcast's `deepDiveEnabled` setting.
- `episode-review`: a focus episode is always reviewable before TTS, and review gains a feedback-driven recompose action that targets the same episode and article set repeatedly.
- `episode-article-tracking`: articles linked to a focus episode are exempted from the "mark as processed" step that normally removes them from future selection.
- `episode-history-lookback`: `searchPastEpisodes` matches distinguish a focus episode from a regular one, and the compose prompt is nudged to treat a focus-episode match as a continuation to build on with new developments, rather than a topic to skip, while still allowing a limited reuse of the same articles.

## Impact

- Backend: `PodcastController`, `PodcastService`, `EpisodeController`, `EpisodeService`, `ArticleScoreSummarizer`, `ChatClientFactory`, `LlmPipeline`, `BriefingComposer`/`DialogueComposer`/`InterviewComposer`, `ComposeContext`, `Episode`/`Podcast` entities and repositories, `ResearchTool`.
- Database: new migration `V79` adding `episodes.focus` and `episodes.review_feedback`; a new table (or column set) to persist per-episode research queries/sources for review display.
- Frontend: `frontend/src/app/podcasts/[podcastId]/upcoming/page.tsx` (Focus field) and the episode detail/review page (research sources, feedback textbox, regenerate-script action).
