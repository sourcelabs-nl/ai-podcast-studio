## Why

The Sources tab lists every source a podcast has ever had, and most of them are dead. After the X sources moved from per-account Nitter feeds to Narro, 37 of the 61 sources are disabled: 36 retired `nitter.net` / `zpravobot.news` accounts plus the superseded `X via Narro` feed. They are kept deliberately — deleting a source removes its posts and articles, and `episode_articles` cascades, which would strip the source attribution from 113 published episodes — so they are not going away.

The result is a table where the 24 sources that actually run are outnumbered three to two by ones that never will, and there is no way to see only what is live.

## What Changes

- `GET /users/{userId}/podcasts/{podcastId}/sources` accepts an optional `enabled` query parameter: `true` returns only enabled sources, `false` only disabled ones, and omitting it returns all, so existing callers are unaffected.
- The filter is applied in the repository query rather than by discarding rows after loading them, so the per-source article, post and breaker lookups that follow only run for the sources being returned.
- The Sources tab filters on the `Enabled` column header, matching the status filter already on the episodes list: a dropdown with All / Enabled / Disabled. It defaults to **Enabled**, so the tab opens showing only live sources.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `podcast-sources`: the list endpoint gains the `enabled` filter.
- `frontend-source-management`: the source table gains the column-header filter and opens filtered to enabled.

## Impact

- Backend: `SourceRepository` (derived query), `SourceService.findByPodcastId` gains an optional `enabled`, `SourceController.list` gains the request parameter.
- Frontend: `sources-tab.tsx` gains the dropdown and refetches when it changes.
- No schema change. Existing API consumers that omit the parameter keep seeing every source.
