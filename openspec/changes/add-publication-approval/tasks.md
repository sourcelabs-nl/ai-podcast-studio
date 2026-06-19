> Retrofit: this change was implemented first, then documented. All tasks reflect completed work.

## 1. Backend: data model

- [x] 1.1 Add Flyway migration `V61__add_publish_approval.sql` adding `podcasts.require_publish_approval INTEGER NOT NULL DEFAULT 0` and `episodes.publish_approved INTEGER NOT NULL DEFAULT 1`
- [x] 1.2 Add `requirePublishApproval: Boolean = false` to the `Podcast` entity
- [x] 1.3 Add `publishApproved: Boolean = true` to the `Episode` entity

## 2. Backend: API surface

- [x] 2.1 Add `requirePublishApproval` to `CreatePodcastRequest`, `UpdatePodcastRequest`, and `PodcastResponse`; map it in `Podcast.toResponse` and through `PodcastController` create/update and `PodcastService` create/update
- [x] 2.2 Add `publishApproved` to `EpisodeResponse` and map it in `Episode.toResponse`

## 3. Backend: approval + gate

- [x] 3.1 Set `publishApproved = !podcast.requirePublishApproval` at every episode creation/finalize path in `EpisodeService`
- [x] 3.2 Add `EpisodeService.approveForPublication` setting `publishApproved = true` and emitting `episode.publication_approved`
- [x] 3.3 Add `POST .../episodes/{episodeId}/approve-publication` to `EpisodeController` (200 for GENERATED, 409 otherwise)
- [x] 3.4 Guard `PublishingService.publish`: reject when `requirePublishApproval && !publishApproved`; map to 409 `approval_required` in `PublishingController`

## 4. Frontend

- [x] 4.1 Add `requirePublishApproval` (Podcast) and `publishApproved` (Episode) to the TypeScript types
- [x] 4.2 Add the "Require approval before publishing" toggle to the podcast settings page
- [x] 4.3 Show an "Approve for publication" button (gating Publish) on the podcast detail and episode detail pages until approved

## 5. Verification

- [x] 5.1 Unit tests: publish gate (blocked/allowed/ignored), approval endpoint (200/409), `approveForPublication`, un-approved episode creation
- [x] 5.2 `mvn test` green; live-verified the settings round-trip and approve-publication endpoint
