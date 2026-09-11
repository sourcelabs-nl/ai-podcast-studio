<!-- Implemented before this change was written; every task below is already done. -->

## 1. Refusal detection

- [x] 1.1 Add `SoundCloudUploadNotPermittedException` to `SoundCloudClient.kt`, documenting that it is distinct from a quota failure and that no deletion can clear it
- [x] 1.2 Translate a `403` from `POST /tracks` whose body contains `subscription is required` into that exception, leaving every other `403` as-is

## 2. Publish ordering

- [x] 2.1 Extract `buildUploadRequest(episode, podcast)` and `episodeDate(episode)` so the upload can be built once and retried
- [x] 2.2 Attempt the upload before freeing quota; free and retry once only when an `HttpClientErrorException` comes back
- [x] 2.3 Make `freeQuotaIfNeeded` return `Boolean`, true only when at least one track was deleted, so a pointless retry is skipped

## 3. Update ordering

- [x] 3.1 Upload the replacement before deleting the track it replaces
- [x] 3.2 Claim the canonical permalink on the replacement after the old track is deleted, and return the reclaimed URL

## 4. Reporting

- [x] 4.1 Parse SoundCloud's `message` field for the exception message, injecting the Spring-managed `JsonMapper`, and log the full body
- [x] 4.2 Handle `SoundCloudUploadNotPermittedException` in `PublishingExceptionHandler` as 403 with `code: "upload_not_permitted"`

## 5. Tests

- [x] 5.1 `publish deletes nothing when the account may not upload at all`
- [x] 5.2 `update keeps the published track when the replacement upload is refused`
- [x] 5.3 `publish reports the refusal when quota is exceeded and nothing can be freed`, replacing the old "uploads anyway" test whose premise no longer holds
- [x] 5.4 Update `publish deletes oldest tracks until episode duration fits then uploads` so the first attempt is refused and the retry succeeds
- [x] 5.5 Update the `update` tests for upload-then-delete ordering and the permalink re-claim
- [x] 5.6 `publish returns 403 with a readable reason when the account may not upload`
- [x] 5.7 Run `mvn test` and confirm the full suite passes
