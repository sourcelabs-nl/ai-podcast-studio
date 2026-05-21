## Why

Once an episode script is approved and audio is generated, there's no way to re-run TTS (e.g., after switching the TTS model or voice) without discarding and regenerating the full episode. Additionally, there's no way to listen to the generated MP3 directly from the UI without downloading or navigating the filesystem.

## What Changes

- New backend endpoint to regenerate only the audio for an episode (re-runs TTS on the existing script, overwrites the previous MP3).
- New backend endpoint to stream the episode's MP3 file so the frontend can play it.
- Episode table and episode detail page: add a play/listen icon-link that streams the MP3 inline.
- Episode detail and table: add a "Regenerate Audio" action button available on GENERATED episodes (and PENDING_REVIEW episodes that already have a script, i.e., where audio was previously generated).

## Capabilities

### New Capabilities

- `audio-regeneration`: Re-run TTS on an existing episode's script, producing a new MP3 and updating the episode's audio file path, duration, TTS model, and cost fields. Available on GENERATED episodes.
- `episode-audio-playback`: Serve the episode's MP3 via a streaming HTTP endpoint. Frontend links (table row and detail page) open an inline audio player or trigger browser playback.

### Modified Capabilities

<!-- None - no existing spec-level requirements change -->

## Impact

- **Backend**: New `POST .../regenerate-audio` endpoint in `EpisodeController`; new `GET .../audio` streaming endpoint (likely in `EpisodeController` or a dedicated `EpisodeAudioController`). New service method in `EpisodeService` or `AudioGenerationService` to kick off async TTS regeneration.
- **Frontend**: `frontend/src/app/podcasts/[podcastId]/page.tsx` (episode table) and `frontend/src/app/podcasts/[podcastId]/episodes/[episodeId]/page.tsx` (detail page) — add play button and regenerate-audio button.
- **No new dependencies** — FFmpeg and TTS providers already in place.