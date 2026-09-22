## 1. Refuse discarding a published episode

- [x] 1.1 Add `EpisodePublishedException` and map it to 409 (`episode_published`) in `PodcastExceptionHandler`
- [x] 1.2 Add `PublishingService.liveTargets` and a new `EpisodeDiscardService` that checks it before calling `EpisodeService.discardOnly`/`discardAndResetArticles`, naming the live targets in the message; `EpisodeController.discard` delegates to it
- [x] 1.3 Update the `EpisodeService` test constructors, move the guard tests into a new `EpisodeDiscardServiceTest` (MockK mocks of `EpisodeService`/`PublishingService`), and add a `PublishingServiceTest` case for `liveTargets`
- [x] 1.4 Show the backend error as a toast when an episode action fails on the episode detail page and the podcast episode list; verify with `npx tsc --noEmit`

## 2. Focus candidates keep the real coverage

- [x] 2.1 Measure episode 228's selection against episode 227's articles and the discard timing in `app.log`
- [x] 2.2 Select focus candidates with `findAllSince` (ignoring `is_processed`) and drop pure retweets
- [x] 2.3 Update and add `ArticleEligibilityServiceTest` cases for used articles, retweets and replies

## 3. Verify

- [x] 3.1 `mvn test` passes
- [x] 3.2 Restart the app and check that discarding published episode 226 returns 409 and leaves it GENERATED and published
