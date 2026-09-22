## Why

Two episodes exposed gaps in behavior that the specs already expect or that a focus episode depends on.

- Episode 229 was discarded while its publication on FTP was `PUBLISHED`. The discard endpoint only checked the episode status, so the audio stayed live while the app treated the episode as gone. The `episode-review` spec already requires this to be refused; the implementation did not enforce it.
- Focus episode 228 ("Claude Opus 5.5 release") composed from five X posts, two of them a retweet and a one-line reply copying the `@claudeai` announcement. The Anthropic announcement page and the MarkTechPost coverage were in the same window, but regular episode 227 had selected them and marked them processed. Focus candidates were limited to unprocessed articles, so the best coverage of the focus was never scored. 227 was discarded nine seconds after 228 was created.

## What Changes

- Discarding an episode (from `PENDING_REVIEW`, `GENERATED` or `FAILED`) is refused with HTTP 409 and the message "Episode is published to <targets>; unpublish it first" when any of its publications is `PUBLISHED`. The check lives in `EpisodeService` and maps to 409 through `PodcastExceptionHandler`.
- The dashboard shows the backend's error message as a toast when an episode action (including discard) fails, on both the episode detail page and the podcast episode list.
- Focus episode candidates are every article in the run's window from the podcast's sources, whether or not another episode already used it. Pure retweets (body starting with `RT @`) are dropped from focus candidates, since they carry no content of their own and a focus episode has no dedup stage.

## Capabilities

### New Capabilities

### Modified Capabilities
- `episode-review`: discard refuses a published episode in every discardable status, with a message naming the targets.
- `article-eligibility`: focus episode candidates include already-used articles and exclude pure retweets.

## Impact

- `EpisodeService` gains `EpisodePublicationRepository`; new `EpisodePublishedException` and its handler.
- `ArticleEligibilityService.findEligibleArticlesForFocus` uses `ArticleRepository.findAllSince`.
- Focus scoring may score more candidates per run (the articles regular episodes used); the existing scoring cost guard still applies.
- Frontend: `episodes/[episodeId]/page.tsx` and `podcasts/[podcastId]/page.tsx` action handlers.
