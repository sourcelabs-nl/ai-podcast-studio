## ADDED Requirements

### Requirement: SoundCloud upload quota recovery
When publishing to SoundCloud fails because the upload quota is exceeded (HTTP 413), the publish wizard SHALL offer to remove the oldest track to free space, and — after a single user confirmation — SHALL delete that track and automatically retry publishing. Because SoundCloud does not free the quota immediately after a deletion, the wizard SHALL retry on a spaced schedule (waiting before each attempt: 8s, 15s, 30s) rather than retrying immediately, and SHALL show a "republishing" progress indicator with the current attempt number while waiting. If the publish still exceeds quota after all retry attempts, the wizard SHALL surface the quota state again (with the current oldest track) so the user can wait or remove another track. The destructive delete SHALL require the one-time confirmation; the subsequent retries SHALL NOT require further user action.

#### Scenario: Quota exceeded offers removal
- **WHEN** publishing returns HTTP 413 with an oldest track
- **THEN** the wizard shows an error with the oldest track and a "Remove & republish" confirmation button

#### Scenario: Confirm triggers delete then auto-retry
- **WHEN** the user confirms removal
- **THEN** the wizard deletes the oldest track, waits before retrying, and automatically re-attempts the publish without further user clicks, showing a "republishing… (attempt N of 3)" indicator

#### Scenario: Auto-retry succeeds after space is freed
- **WHEN** a retry attempt succeeds after the deletion has propagated
- **THEN** the wizard advances to the success result with the external URL

#### Scenario: Still over quota after retries
- **WHEN** every retry attempt still returns HTTP 413
- **THEN** the wizard surfaces the quota state again so the user can wait or remove another track

#### Scenario: Done disabled during retry
- **WHEN** an auto-retry sequence is in progress
- **THEN** the "Done" button is disabled until the sequence completes
