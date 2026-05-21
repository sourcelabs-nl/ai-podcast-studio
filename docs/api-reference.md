# API Reference

All endpoints are served from `http://localhost:8085` by default. Most resources are scoped under `/users/{userId}/...` so multiple users can coexist on a single installation.

## Users

```
POST   /users                                        — Create a user
GET    /users                                        — List all users
GET    /users/{userId}                               — Get user
PUT    /users/{userId}                               — Update user
DELETE /users/{userId}                               — Delete user (cascades)
```

## Podcasts

```
POST   /users/{userId}/podcasts                      — Create a podcast
GET    /users/{userId}/podcasts                      — List podcasts
GET    /users/{userId}/podcasts/{podcastId}          — Get podcast
PUT    /users/{userId}/podcasts/{podcastId}          — Update podcast
DELETE /users/{userId}/podcasts/{podcastId}          — Delete podcast (cascades)
POST   /users/{userId}/podcasts/{podcastId}/generate            — Manually trigger episode generation
GET    /users/{userId}/podcasts/{podcastId}/feed.xml            — RSS 2.0 feed for podcast apps
GET    /users/{userId}/podcasts/{podcastId}/upcoming-articles   — Articles collected for next episode
GET    /users/{userId}/podcasts/{podcastId}/preview             — Preview script (SSE stream)
POST   /users/{userId}/podcasts/{podcastId}/image               — Upload podcast image
GET    /users/{userId}/podcasts/{podcastId}/image               — Retrieve podcast image
DELETE /users/{userId}/podcasts/{podcastId}/image               — Delete podcast image
```

### Example: create a customized podcast

```bash
curl -X POST http://localhost:8085/users/{userId}/podcasts \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "AI Weekly",
    "topic": "artificial intelligence and machine learning",
    "language": "en",
    "style": "deep-dive",
    "llmModels": {"compose": {"provider": "openrouter", "model": "anthropic/claude-opus-4.7"}},
    "ttsProvider": "openai",
    "ttsVoices": {"default": "onyx"},
    "ttsSettings": {"speed": 1.1},
    "targetWords": 2000,
    "relevanceThreshold": 6,
    "requireReview": true,
    "cron": "0 0 8 * * MON",
    "timezone": "Europe/Amsterdam",
    "customInstructions": "Focus on recent breakthroughs and industry trends"
  }'
```

## Episodes

```
GET    /users/{userId}/podcasts/{podcastId}/episodes              — List episodes (optional ?status= filter)
GET    /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}  — Get episode (includes cost tracking fields)
PUT    /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/script  — Edit script (PENDING_REVIEW only)
POST   /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/approve          — Approve and trigger TTS generation
POST   /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/discard          — Discard episode
POST   /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/regenerate       — Re-compose script from same articles
POST   /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/regenerate-audio — Re-run TTS on existing script
POST   /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/regenerate-recap — Re-generate recap and show-notes from existing script
POST   /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/retry            — Retry failed episode from the stage that failed
GET    /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/audio            — Stream the episode MP3
GET    /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/articles         — List articles used in episode
```

Episode statuses: `PENDING_REVIEW` → `APPROVED` → `GENERATING_AUDIO` → `GENERATED` (or `FAILED`). Episodes can also be `DISCARDED`. The review endpoints are only relevant when `requireReview` is enabled on the podcast. `FAILED` episodes preserve their `pipelineStage` so they can be resumed via `retry` without re-running earlier LLM work.

## Sources

```
POST   /users/{userId}/podcasts/{podcastId}/sources             — Add source
GET    /users/{userId}/podcasts/{podcastId}/sources             — List sources
PUT    /users/{userId}/podcasts/{podcastId}/sources/{sourceId}  — Update source
DELETE /users/{userId}/podcasts/{podcastId}/sources/{sourceId}  — Delete source
```

Sources can be of type `rss`, `website`, or `twitter`. Each source has a configurable `pollIntervalMinutes` and can be toggled with `enabled`. Twitter sources require an X OAuth connection (see [publishing.md](publishing.md)). An optional `aggregate` field (boolean) controls whether posts are merged into a single digest article at briefing generation time, useful for short-form sources like tweets. When `null` (default), aggregation is auto-detected for `twitter` type sources and nitter.net RSS feeds. An optional `categoryFilter` field (comma-separated terms) filters RSS entries by category tags. An optional `label` field provides a display name for the source in the dashboard.

When adding a source, the URL is validated by performing a test fetch: RSS feeds must return valid XML with at least one item, and websites must return extractable content. Invalid URLs are rejected with HTTP 422 and a descriptive error message. Twitter sources skip URL validation (they use OAuth).

Posts older than `app.source.max-article-age-days` (default: 7) are skipped during ingestion and periodically cleaned up. Newly added sources only ingest content published after the source was created, preventing historical backlog from flooding into existing briefings. Additionally, posts are deduplicated across all sources within the same podcast: if two sources (e.g., a Twitter account and its Nitter RSS mirror) produce identical content, only the first copy is kept.

Source list responses include `articleCount`, `relevantArticleCount`, and `postCount` fields: the total number of articles collected from the source, how many scored at or above the podcast's `relevanceThreshold`, and the total number of raw posts ingested. Counts are computed in single batch queries for efficiency.

Source responses include failure tracking fields: `consecutiveFailures`, `lastFailureType` (`"transient"` or `"permanent"`), and `disabledReason`. Sources that fail repeatedly use **exponential backoff**: the poll interval doubles with each consecutive failure, capped at `app.source.max-backoff-hours` (default: 24). Sources with **permanent** errors (404, 410, 401, 403, DNS failure) are auto-disabled after `app.source.max-failures` (default: 15) consecutive failures. Transient errors (timeouts, 5xx, rate limits) trigger backoff but never auto-disable. Re-enabling a disabled source via the API resets all failure tracking.

Sources sharing the same host are polled sequentially with a configurable delay between requests, preventing rate limit violations on free/community-run services (e.g., Nitter instances). Different hosts are polled in parallel using Kotlin coroutines. On first boot, sources receive random startup jitter to prevent all sources from polling simultaneously. The delay between same-host polls is resolved using a three-layer precedence chain: per-source `pollDelaySeconds` field > host-specific override (`app.source.host-overrides.<host>.poll-delay-seconds`) > source-type default (`app.source.poll-delay-seconds.<type>`) > 0.

## Publishing

```
POST   /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/publish/{target}       — Publish episode to target
DELETE /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/publications/{target}   — Unpublish episode from target
GET    /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/publications            — List publications for episode
```

## Publication Targets

```
GET    /users/{userId}/podcasts/{podcastId}/publication-targets              — List configured targets
PUT    /users/{userId}/podcasts/{podcastId}/publication-targets/{target}     — Configure target (ftp, soundcloud)
DELETE /users/{userId}/podcasts/{podcastId}/publication-targets/{target}     — Remove target configuration
POST   /users/{userId}/publishing/test/ftp                                  — Test FTP connection
POST   /users/{userId}/publishing/test/soundcloud                           — Test SoundCloud connection
```

## SoundCloud OAuth

```
GET    /users/{userId}/oauth/soundcloud/authorize          — Get SoundCloud authorization URL
GET    /oauth/soundcloud/callback                          — OAuth callback (handled automatically)
GET    /users/{userId}/oauth/soundcloud/status              — Check connection status (includes quota)
DELETE /users/{userId}/oauth/soundcloud/tracks/{trackId}    — Delete a SoundCloud track
DELETE /users/{userId}/oauth/soundcloud                     — Disconnect SoundCloud
```

## X (Twitter) OAuth

```
GET    /users/{userId}/oauth/x/authorize  — Get X authorization URL
GET    /oauth/x/callback                  — OAuth callback (handled automatically)
GET    /users/{userId}/oauth/x/status      — Check connection status
DELETE /users/{userId}/oauth/x             — Disconnect X account
```

## Real-Time Events

```
GET    /users/{userId}/events                  — SSE event stream (pipeline progress, episode updates)
```

## Voices

```
GET    /users/{userId}/voices?provider=elevenlabs  — List available ElevenLabs voices
GET    /users/{userId}/voices?provider=inworld     — List available Inworld AI voices
```

## Provider Configuration

```
GET    /users/{userId}/api-keys              — List configured providers
PUT    /users/{userId}/api-keys/{category}   — Set provider (LLM, TTS, or RESEARCH)
DELETE /users/{userId}/api-keys/{category}   — Remove provider config
```

Users can configure their own LLM, TTS, and research providers. Supported LLM providers: `openrouter`, `openai`, `ollama`. Supported TTS providers: `openai`, `elevenlabs`, `inworld`. Supported research providers: `tavily`. API keys are stored encrypted (AES-256).
