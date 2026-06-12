## 1. Backend: auto_publish data model

- [ ] 1.1 Add Flyway migration `V60__add_autopublish_to_publication_targets.sql` adding `auto_publish INTEGER NOT NULL DEFAULT 0` to `podcast_publication_targets`
- [ ] 1.2 Add `autoPublish: Boolean = false` to the `PodcastPublicationTarget` entity
- [ ] 1.3 Update `PodcastPublicationTargetRepositoryCustomImpl.upsert` to insert/update and read back the `auto_publish` column
- [ ] 1.4 Add `autoPublish` to `PodcastPublicationTargetService.upsert` and thread it through

## 2. Backend: API surface

- [ ] 2.1 Add `autoPublish: Boolean = false` to `PublicationTargetRequest` and `autoPublish: Boolean` to `PublicationTargetResponse`
- [ ] 2.2 Map `autoPublish` in `PodcastPublicationTarget.toResponse`
- [ ] 2.3 Pass `request.autoPublish` from `PodcastPublicationTargetController.upsert` into the service

## 3. Backend: auto-publish trigger

- [ ] 3.1 Add `AutoPublishListener` in the `publishing` package: `@EventListener` filtering `PodcastEvent.event == "episode.generated"`, resolving podcast + episode, and publishing each `enabled && autoPublish` target via `PublishingService.publish` on a `Dispatchers.IO` coroutine scope, per-target try/catch

## 4. Frontend

- [ ] 4.1 Add `autoPublish` to the `PublicationTarget` type and `pubForm` state in the podcast settings page
- [ ] 4.2 Add an "Auto-publish on generate" toggle per target in the Publishing tab; include `autoPublish` in the PUT body
- [x] 4.3 Add a "Delete podcast" danger zone on the podcast detail page with a typed-name `AlertDialog` (confirm disabled until name matches), `DELETE` call, and redirect to `/podcasts`

## 5. Verification

- [ ] 5.1 `mvn test` passes (add/adjust tests for the upsert and listener as needed)
- [ ] 5.2 Run `/code-review --all` and resolve violations
- [ ] 5.3 Restart the app and verify auto-publish fires on generation and delete works end-to-end
