## Context

Discard is handled in `EpisodeController.discard`, which checks the episode status and calls `EpisodeService.discardOnly` (FAILED) or `discardAndResetArticles`. Publications live in `episode_publications` with a `PublicationStatus`.

Focus candidates come from `ArticleEligibilityService.findEligibleArticlesForFocus`, which then goes through `filterToWindow` (evergreen drop plus window filter), scoring against the focus text, and the relevance threshold. There is no dedup stage for a focus episode.

Evidence for episode 228 (from `GET .../episodes/{id}/articles` and `app.log`):
- 228 was created at 20:21:19Z with window [14:00Z, 20:21Z); the eligibility log reports 39 candidates, and 5 cleared the threshold, all X posts.
- Episode 227 (regular, window [14:00Z, 20:00Z)) held `anthropic.com/claude-opus-5-5` (twice), the MarkTechPost release article and `@rlancemartin`'s original post, all scored 10 or 9 on topic. 227 was discarded at 20:21:28Z, after 228 had selected.
- Of 228's five articles, `250789` is a pure retweet of `@claudeai` and `250797` is the same announcement with "much wow" appended. The other three carry their own text; their titles look empty because of how X replies are titled, not because the bodies are.

## Goals / Non-Goals

**Goals:**
- A published episode cannot be discarded through the API.
- A focus episode sees the articles that best report its focus, even when a regular episode of the same window used them.
- Posts that only repeat another post are not focus input.

**Non-Goals:**
- Changing the focus scoring prompt or threshold: the X posts that were kept do report on the focus, and the real articles were never scored, so the prompt was not the cause.
- A dedup stage for focus episodes.
- Filtering replies by length: the kept replies carried real content.

## Decisions

- **Guard in a dedicated `EpisodeDiscardService`, not `EpisodeService` or the controller.** The natural place for a live-publication check is `PublishingService`, but `EpisodeService` cannot depend on it: `PublishingService` depends on `PublisherRegistry`, which depends on `FtpPublisher`, which depends on `EpisodeService`, so a direct dependency would be a bean cycle. `EpisodeDiscardService` sits above both, calling `PublishingService.liveTargets(episodeId)` for the check and `EpisodeService.discardOnly`/`discardAndResetArticles` for the actual discard. It throws `EpisodePublishedException` (an `IllegalStateException`, like the other typed podcast failures) when any target is still live, which `PodcastExceptionHandler` maps to 409 with code `episode_published`. `EpisodeController.discard` delegates to `EpisodeDiscardService.discard` after its existing status check; every caller of discard goes through this service, so nothing can bypass the guard.
- **Focus candidates ignore `is_processed`.** A focus episode is an extra deep dive on one story; the regular episode having covered the story is the normal case, not a reason to exclude it. `findAllSince` already exists, so no new query is needed.
- **Drop pure retweets by body prefix.** X and Narro retweets arrive with a body starting `RT @`. This is the one case with no own content; replies and quote posts keep their own text and stay.

## Risks / Trade-offs

- More candidates per focus run means more scoring calls. The pre-flight scoring cost guard still refuses a run above the podcast's threshold.
- Discarding a focus episode resets `is_processed` on its articles unless they are linked to a published episode, so an article shared with an unpublished regular episode becomes eligible again for the next regular run.
