## Why

Publishing an episode to FTP/SoundCloud is currently a manual, per-episode action: a user must open the publish wizard and click publish for every generated episode. For podcasts that run on a schedule, this is repetitive toil and episodes can sit unpublished. Separately, there is no way to remove a podcast from the dashboard even though the backend already supports cascade deletion, so stale podcasts accumulate with no safe UI to delete them.

## What Changes

- Add a per-target **auto-publish** option to each podcast publication target (FTP and SoundCloud independently). When enabled, the episode is published automatically as soon as it reaches `GENERATED` status (after any required review/approval), reusing the existing manual publishing pipeline.
- Auto-publish is driven by an event listener on the existing `episode.generated` event; each enabled target is published independently so one failure does not block the others. Failures surface through the existing publication status (`FAILED`) and event stream, not by throwing.
- Surface an **Auto-publish on generate** toggle per target in the podcast settings Publishing tab, persisted via the existing publication-targets API.
- Add a **Delete podcast** action to the podcast detail page with a destructive confirmation dialog that requires the user to type the exact podcast name before deletion is allowed. The backend delete endpoint and cascade already exist; this is the missing UI.

## Capabilities

### New Capabilities
- `episode-auto-publishing`: Automatically publishing an episode to its enabled auto-publish targets when the episode reaches GENERATED, reusing the manual publishing pipeline and recording per-target success/failure.

### Modified Capabilities
- `publication-targets`: The per-podcast publication target gains an `autoPublish` flag (persisted as `auto_publish`) alongside the existing `enabled` flag, exposed through the publication-targets API.
- `frontend-publication-settings`: The Publishing settings tab gains a per-target auto-publish toggle.
- `frontend-dashboard`: The podcast detail page gains a Delete podcast action with typed-name confirmation (backend delete capability already exists).

## Impact

- **Backend**: New Flyway migration `V62` adding `auto_publish` to `podcast_publication_targets`; `PodcastPublicationTarget` entity, its custom `upsert`, `PodcastPublicationTargetService.upsert`, `PublicationTargetRequest`/`PublicationTargetResponse` DTOs and `toResponse` mapper gain the new field; new `AutoPublishListener` in the `publishing` package listening on `episode.generated`. No change to the existing manual `PublishingService.publish` contract.
- **Frontend**: `frontend/src/app/podcasts/[podcastId]/settings/page.tsx` (auto-publish toggle), `frontend/src/app/podcasts/[podcastId]/page.tsx` (delete danger zone + typed-name AlertDialog), and the `PublicationTarget` type.
- **APIs**: `PUT /users/{userId}/podcasts/{podcastId}/publication-targets/{target}` request/response gain `autoPublish`. Existing `DELETE /users/{userId}/podcasts/{podcastId}` is reused unchanged.
- **Dependencies**: None new.
