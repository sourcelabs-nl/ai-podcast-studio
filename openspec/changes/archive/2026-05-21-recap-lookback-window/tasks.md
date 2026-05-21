## 1. Database & Configuration

- [x] 1.1 Add Flyway migration: `ALTER TABLE podcasts ADD COLUMN recap_lookback_episodes INTEGER`
- [x] 1.2 Add `recapLookbackEpisodes: Int? = null` field to `Podcast` entity
- [x] 1.3 Add `app.episode.recap-lookback-episodes: 7` to `application.yaml`
- [x] 1.4 Add config property class/binding for the new global default

## 2. Repository

- [x] 2.1 Add `findRecentByPodcastId(podcastId, limit)` query to `EpisodeRepository` — returns N most recent episodes with non-null recaps, ordered by `generated_at DESC`

## 3. Composer Interface Changes

- [x] 3.1 Update `BriefingComposer.compose()` — change `previousEpisodeRecap: String?` to `previousEpisodeRecaps: List<String>`, update `recapBlock` to list numbered recaps with deduplication instructions
- [x] 3.2 Update `DialogueComposer.compose()` — same parameter change, update `recapBlock` with dialogue-appropriate deduplication instructions
- [x] 3.3 Update `InterviewComposer.compose()` — same parameter change, update `recapBlock` with interview-appropriate deduplication instructions

## 4. Pipeline Integration

- [x] 4.1 Update `LlmPipeline.run()` — resolve effective lookback (podcast override or global default), fetch N recaps via new repository method, pass `List<String>` to composers
- [x] 4.2 Update `LlmPipeline.preview()` — same lookback logic as `run()`

## 5. API Layer

- [x] 5.1 Add `recapLookbackEpisodes` field to podcast create/update DTOs with `@JsonProperty` annotation
- [x] 5.2 Propagate field through `PodcastService.create()` and `PodcastService.update()`
- [x] 5.3 Include field in podcast GET response DTO

## 6. Tests

- [x] 6.1 Update `BriefingComposerTest` — test with empty list, single recap, and multiple recaps
- [x] 6.2 Update `DialogueComposerTest` — test with empty list, single recap, and multiple recaps
- [x] 6.3 Update `InterviewComposerTest` — test with empty list, single recap, and multiple recaps
- [x] 6.4 Update `LlmPipelineTest` — test lookback resolution (podcast override vs global default) and multi-recap fetching
- [x] 6.5 Fix any other broken tests caused by the composer signature change

## 7. Recap Lookback Quality

- [x] 7.1 Filter recap lookback query to only include `GENERATED` episodes (skip pending review, discarded, failed)
- [x] 7.2 Add `onProgress` callback to `LlmPipeline.recompose()` so regeneration emits SSE progress events

## 8. Frontend — Regenerate Episode

- [x] 8.1 Add Regenerate button (`RefreshCw` icon) to episode list page for `PENDING_REVIEW` and `DISCARDED` episodes (not `GENERATED` — must discard first)
- [x] 8.2 Add Regenerate button to episode detail page for `PENDING_REVIEW` and `DISCARDED` episodes
- [x] 8.3 Hide Regenerate button if any episode on the same day has been published
- [x] 8.4 Add confirmation dialog (AlertDialog) before triggering regeneration
- [x] 8.5 Show loading state (spinning icon) during regeneration, navigate to new episode on success
