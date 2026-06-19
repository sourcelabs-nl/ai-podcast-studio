## Why

A podcast can already require human **review before audio generation** (`requireReview`): the script waits in `PENDING_REVIEW` until a user approves it. There is no equivalent gate on the publishing side. Once an episode reaches `GENERATED`, anyone can publish it immediately. For podcasts that want a sign-off step before content goes public, an optional approval gate on publishing is missing.

## What Changes

- Add a per-podcast **`requirePublishApproval`** flag, mirroring `requireReview`. When enabled, a newly generated episode is created **not yet approved for publication**, and the Publish action is blocked until it is approved.
- Add a per-episode **`publishApproved`** flag. Existing episodes default to approved so they remain publishable; new episodes under `requirePublishApproval` are created un-approved.
- Add an **Approve for publication** action (`POST .../episodes/{id}/approve-publication`) that sets `publishApproved = true` on a `GENERATED` episode.
- **Gate publishing**: `PublishingService.publish` rejects an episode when `requirePublishApproval` is on and the episode is not approved (mapped to HTTP 409 `approval_required`).
- Surface the gate in the UI: a "Require approval before publishing" toggle in podcast settings, and an **Approve for publication** button (replacing Publish) on the episode list and detail pages until the episode is approved.

## Capabilities

### New Capabilities
- `episode-publication-approval`: An optional per-podcast gate that requires a generated episode to be explicitly approved for publication before it can be published, with a dedicated approval action and a publish-time guard.

## Impact

- **Backend**: Flyway migration `V61` adds `podcasts.require_publish_approval` (default 0) and `episodes.publish_approved` (default 1). `Podcast` and `Episode` entities, podcast create/update DTOs + mappers + service, and the `EpisodeResponse` gain the new fields. New `EpisodeService.approveForPublication` and `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/approve-publication`. `PublishingService.publish` gains the approval guard; `PublishingController` maps it to 409 `approval_required`.
- **Frontend**: settings toggle (`settings/page.tsx`), Approve-for-publication button + Publish gating on the podcast detail (`[podcastId]/page.tsx`) and episode detail (`episodes/[episodeId]/page.tsx`) pages, and `requirePublishApproval` / `publishApproved` added to the TypeScript types.
- **APIs**: podcast create/update/response gain `requirePublishApproval`; episode response gains `publishApproved`; new approve-publication endpoint.
- **Dependencies**: None new.
