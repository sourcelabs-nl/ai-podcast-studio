## Context

Episodes are generated through a pipeline that ends with TTS audio generation. Once an episode reaches `GENERATED` status, the audio file path, duration, TTS model, and TTS cost are stored on the `Episode` entity. There is currently no way to re-run only the TTS step without discarding and fully regenerating the episode. The MP3 files are stored on disk under `{episodes_dir}/{podcastId}/episodes/briefing-{timestamp}.mp3` and are not exposed via HTTP.

Key existing components:
- `TtsPipeline.generateForExistingEpisode(episode, podcast)` — runs TTS on an existing episode's script
- `AudioGenerationService.generateAudioAsync(episodeId, podcastId)` — async wrapper used during the normal approval flow
- `EpisodeController` — handles episode-scoped actions (approve, discard, retry, regenerate-recap)

## Goals / Non-Goals

**Goals:**
- Allow re-running TTS on a GENERATED episode's existing script (e.g., after switching TTS model/voice)
- Expose the episode MP3 via a streaming HTTP endpoint so the browser can play it
- Add a play button on both the episode table row and the episode detail page
- Add a "Regenerate Audio" action button on GENERATED episodes (detail page + table row)

**Non-Goals:**
- Changing the script, articles, show notes, or any other episode data
- Supporting playback on episodes that don't yet have audio (non-GENERATED)
- Streaming partial/progressive audio generation during TTS

## Decisions

### D1: Reuse `AudioGenerationService` for audio regeneration

The existing `AudioGenerationService.generateAudioAsync()` already handles the full async TTS lifecycle: status transitions (`GENERATING_AUDIO`), error handling, duration/cost updates, and file path persistence. Rather than duplicating that logic, we add an overloaded or separate method `regenerateAudioAsync(episodeId, podcastId)` that resets the relevant audio fields and delegates to the same TTS pipeline.

**Alternative considered:** A brand-new service method in `EpisodeService`. Rejected because `AudioGenerationService` already owns the TTS orchestration concern.

### D2: Episode status during audio regeneration

When audio regeneration is triggered, the episode transitions back to `GENERATING_AUDIO` and returns to `GENERATED` on success. This reuses the existing status, avoids a new enum value, and makes the UI's existing progress indicators work without change.

**Alternative considered:** A new status `REGENERATING_AUDIO`. Rejected as unnecessary complexity — the semantics of `GENERATING_AUDIO` are identical.

### D3: MP3 streaming via a dedicated endpoint on `EpisodeController`

`GET /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/audio` reads the file from `Episode.audioFilePath` and streams it as `audio/mpeg`. The controller delegates path resolution to a small helper on `EpisodeService`; the actual file streaming uses Spring's `Resource` / `InputStreamResource`.

**Alternative considered:** Serve files via a static resource mapping. Rejected because episodes are per-user/podcast and the path must be validated against the requesting user's data — a controller-level check is simpler and more secure.

### D4: Frontend playback via native `<audio>` or browser navigation

The play button links to the `/audio` endpoint. Clicking it opens the browser's native audio player (a new tab or inline HTML5 `<audio>` element). We use an `<a href=... target="_blank">` with an audio icon rather than embedding a full player widget, keeping the implementation minimal.

## Risks / Trade-offs

- **Re-run overwrites previous audio file**: The new MP3 replaces the old one. If TTS fails mid-way, the episode may briefly have no audio. Mitigation: keep the old file until the new one is fully written, then swap paths (already handled by `TtsPipeline` which writes to a new timestamped filename each run).
- **Cost**: Regenerating audio re-charges TTS tokens. No mitigation — this is intentional user action; the cost delta is visible in the episode detail.
- **Large file streaming**: MP3 files can be tens of MB. Mitigation: use `InputStreamResource` with proper `Content-Length` and `Accept-Ranges` headers so the browser can seek without downloading the entire file.