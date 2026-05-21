## 1. Backend: Audio Regeneration Endpoint

- [x] 1.1 Add `regenerateAudioAsync(episodeId, podcastId)` method to `AudioGenerationService` that resets audio fields and re-runs TTS via `TtsPipeline.generateForExistingEpisode()`
- [x] 1.2 Add `POST /{episodeId}/regenerate-audio` endpoint to `EpisodeController` that validates the episode is GENERATED (HTTP 409 otherwise) and delegates to `AudioGenerationService.regenerateAudioAsync()`

## 2. Backend: Audio Streaming Endpoint

- [x] 2.1 Add `GET /{episodeId}/audio` endpoint to `EpisodeController` that reads `Episode.audioFilePath`, validates the file exists on disk, and streams it as `audio/mpeg` using `InputStreamResource`
- [x] 2.2 Return HTTP 404 when `audioFilePath` is null or the file does not exist on disk

## 3. Frontend: Play Button (Episode Table)

- [x] 3.1 In `frontend/src/app/podcasts/[podcastId]/page.tsx`, add a play icon-link (`Volume2` from lucide-react) in the Actions column for episodes where `audioFilePath` is set; link to `/api/users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/audio` with `target="_blank"`

## 4. Frontend: Play Button (Episode Detail Page)

- [x] 4.1 In `frontend/src/app/podcasts/[podcastId]/episodes/[episodeId]/page.tsx`, add a play icon-link in the header area for episodes with audio, linking to the `/audio` endpoint with `target="_blank"`

## 5. Frontend: Regenerate Audio Button (Episode Detail Page)

- [x] 5.1 In the episode detail page, add a "Regenerate Audio" action button (visible when status is `GENERATED`) that calls `POST .../regenerate-audio` and then refreshes episode state

## 6. Frontend: Regenerate Audio Button (Episode Table)

- [x] 6.1 In the episode table, add a "Regenerate Audio" per-row action button for GENERATED episodes that calls `POST .../regenerate-audio` and triggers a local episode state refresh