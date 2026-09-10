## ADDED Requirements

### Requirement: A successful upload's record survives its side effects
Once `publisher.publish()` returns, the system SHALL record the publication as `PUBLISHED` with the returned `external_id` and `external_url`, and no later step SHALL alter that record. The publisher's `postPublish` hook, the SoundCloud playlist rebuild and the static feed export SHALL each run outside the block that records a publishing failure, and each SHALL be guarded on its own: a failure is logged with the step that failed and the publication stands.

None of these steps can undo an upload, so none of them may deny it. Letting them do so cost episode 202 its SoundCloud track id: the upload had succeeded, a later step was cancelled, and the catch saved the pre-upload snapshot over the row. With the id gone, the same-day replacement (which considers only `PUBLISHED` rows) could not remove the old track, and the regenerated episode's upload was rejected over a permalink still held by it.

The same shape SHALL apply when an already-published episode is updated: the update call decides the outcome, and the side effects that follow it do not.

#### Scenario: A failing post-publish hook leaves the publication published
- **WHEN** an upload to SoundCloud succeeds and the publisher's `postPublish` hook then throws
- **THEN** the record stays `PUBLISHED` with the returned external id, the failure is logged, and the publish call returns successfully

#### Scenario: A failing feed export leaves the publication published
- **WHEN** an upload succeeds and the static feed export then throws
- **THEN** the record stays `PUBLISHED` and the failure is logged

#### Scenario: A failing playlist rebuild leaves the publication published
- **WHEN** an upload to SoundCloud succeeds and the playlist rebuild then throws
- **THEN** the record stays `PUBLISHED` and the failure is logged

#### Scenario: A failing side effect after an update keeps the new external id
- **WHEN** an update returns a new external id and a side effect then throws
- **THEN** the record holds the new external id and carries no error message

#### Scenario: The upload itself failing is still recorded
- **WHEN** `publisher.publish()` throws
- **THEN** the record is saved as `FAILED` with the error message and an `episode.publish.failed` event is emitted

### Requirement: A cancelled publish is not recorded as a failure
When a publish or update is cancelled, the system SHALL rethrow the `CancellationException` and leave the publication record as it stands: `PENDING` for a publish that had claimed the record, unchanged for an update. It SHALL NOT be recorded as `FAILED`.

Cancellation says nothing about whether the upload landed. Recording `FAILED` with a null external id asserts both that it did not and that there is nothing remote to clean up, and a later same-day replacement acts on that. `PENDING` is what the system actually knows, and it lets a retry proceed: `publish()` re-claims any record that is not `PUBLISHED`.

#### Scenario: A cancelled upload leaves the record pending
- **WHEN** `publisher.publish()` is cancelled
- **THEN** the record is left `PENDING`, no `FAILED` status is written, and the `CancellationException` propagates

#### Scenario: A cancelled update leaves the record untouched
- **WHEN** `publisher.update()` is cancelled
- **THEN** no error message is stored and the `CancellationException` propagates

#### Scenario: A cancelled side effect does not undo the publication
- **WHEN** a side effect after a successful upload is cancelled
- **THEN** the record stays `PUBLISHED` and the `CancellationException` propagates rather than being swallowed
