## Why

Episodes are repeating the same news stories across consecutive days because the composer only receives a 2-3 sentence recap from the single most recent episode. When different sources publish separate articles about the same announcement (e.g., Claude Code 1M context), each gets a unique content hash and passes deduplication, leading the LLM to present it as new news again. A wider lookback window with explicit deduplication instructions will prevent this.

## What Changes

- Widen the recap context passed to composers from 1 episode to a configurable number (default: 7)
- Add a `recap_lookback_episodes` field to the `podcasts` table (per-podcast override)
- Add a global default `app.episode.recap-lookback-episodes` config property
- Update all three composers (Briefing, Dialogue, Interview) to receive multiple recaps and instruct the LLM to actively avoid repeating previously covered topics
- Update `LlmPipeline` to fetch N recent episodes instead of 1

## Capabilities

### New Capabilities

_(none — this extends existing capabilities)_

### Modified Capabilities

- `episode-continuity`: Widen lookback from 1 to N episodes, change prompt instructions from "weave in references" to "avoid repeating topics"
- `podcast-customization`: Add `recapLookbackEpisodes` as a per-podcast configurable field

## Impact

- `LlmPipeline.kt` — fetch N recent episodes instead of 1; add `onProgress` callback to `recompose()`
- `BriefingComposer.kt`, `DialogueComposer.kt`, `InterviewComposer.kt` — accept list of recaps, update prompt block
- `EpisodeRepository.kt` — new query for N most recent episodes (filtered to GENERATED status only)
- `Podcast` entity + DTO — new nullable field `recap_lookback_episodes`
- `PodcastService` / `PodcastController` — propagate new field in CRUD; emit SSE progress events during regeneration
- `application.yaml` — new config property
- Flyway migration — add column to `podcasts` table
- Frontend settings — add field to podcast settings form
- Frontend episode list + detail pages — add Regenerate button with confirmation dialog (PENDING_REVIEW and DISCARDED only, hidden if same-day published episode exists)