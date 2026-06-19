> Retrofit: implemented first, then documented. All tasks reflect completed work.

## 1. Backend: duration-aware quota plan

- [x] 1.1 Add `QuotaDeletionPlan` / `QuotaTrackToDelete` to `PublishingTypes.kt`; move `SoundCloudQuotaExceededException` there carrying the plan
- [x] 1.2 In `SoundCloudPublisher.publish`, replace the `uploadSecondsLeft <= 0` guard with `planQuotaDeletion`: compare against `episode.durationSeconds` (legacy `<= 0` when duration is unknown), add a buffer, and select oldest podcast tracks until enough is freed
- [x] 1.3 Add `SoundCloudPublisher.deleteTracks(userId, trackIds)`

## 2. Backend: free-and-publish + exception advice

- [x] 2.1 Add `PublishingService.freeQuotaAndPublish` (delete consented tracks, then publish; re-check re-throws a fresh plan)
- [x] 2.2 Add `POST .../publish/{target}/free-and-publish` with `FreeQuotaRequest { trackIds }`
- [x] 2.3 Add `PublishingExceptionHandler` (`@RestControllerAdvice(assignableTypes = [PublishingController::class])`) mapping `SoundCloudQuotaExceededException` to 413 with the plan; move the other publish-path mappings into it and remove the inline try/catch from the publish endpoints

## 3. Frontend

- [x] 3.1 Rewrite `publish-wizard.tsx`: single publish call; on 413 show the server-computed `tracksToDelete` for one-time consent; call `free-and-publish`; remove `RETRY_DELAYS_MS`, the retry loop, and the client-side delete call

## 4. Verification

- [x] 4.1 Unit tests: deletion-plan selection by duration, `deleteTracks`, `freeQuotaAndPublish` (deletes then publishes; rejects non-soundcloud), controller 413 + free-and-publish 200/413
- [x] 4.2 `mvn test` green; frontend `tsc --noEmit` clean
- [x] 4.3 `/code-review` on changed files; restart app, V62 applied, publication-targets API round-trip verified

## 5. Follow-up (not in this change)

- [ ] 5.1 Replace the remaining `IllegalStateException` message-sniffing in `PublishingExceptionHandler.handleIllegalState` (and the inline 404 in `unpublish`) with distinct domain exception types per spring-boot Rule SB8. Pre-existing pattern relocated, not introduced here; full fix touches `PublishingService` + `SoundCloudTokenManager` and their tests.
