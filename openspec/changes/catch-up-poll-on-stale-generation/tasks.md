# Tasks

- [x] Add `lastPollRoundCompletedAt` (in-memory, `@Volatile`) to `SourcePollingScheduler`, set at the end of each successful `pollSources()` round; expose a read-only getter.
- [x] Add `suspend fun pollPodcastSourcesNow(podcastId)` (polls the podcast's enabled sources via the existing host-group logic) and a blocking `catchUpPoll(podcastId)` wrapper.
- [x] Add `app.source.stale-round-threshold-minutes` (default 10) to `SourceProperties` + `application.yaml`.
- [x] In `BriefingGenerationScheduler`, inject `SourcePollingScheduler` + `AppProperties`; before `generateBriefing`, if the last poll round is null or older than the threshold, run `catchUpPoll(podcast.id)` first and log it.
- [x] Update `BriefingGenerationSchedulerTest` constructions for the new deps; stub a fresh `lastPollRoundCompletedAt` so existing tests don't trigger a catch-up; add a test that a stale/absent round triggers `catchUpPoll` before `generateBriefing`.
- [x] Add a `SourcePollingSchedulerTest` case asserting `lastPollRoundCompletedAt` is set after a round and that `pollPodcastSourcesNow` polls only the podcast's sources.
- [x] Run `mvn test` (full suite green) and restart the app.
