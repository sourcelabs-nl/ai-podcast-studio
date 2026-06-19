## Why

SoundCloud caps free upload time (the account runs out of quota). When publishing hits this cap, the API returns HTTP 413 with the oldest track. The publish wizard previously showed a manual "Remove oldest track" button, but after deleting the user had to click Publish again themselves — and that re-publish usually failed, because SoundCloud needs time to process the deletion before the freed quota becomes available. The result was a confusing delete-then-fail-then-retry loop.

## What Changes

- Keep the one-time confirmation for the destructive delete, but after the user confirms, the wizard **deletes the oldest track and automatically retries publishing** on a spaced schedule, giving SoundCloud time to free the quota before each attempt.
- If the upload still exceeds quota after all retries, the wizard surfaces the quota state again so the user can wait longer or remove another track.

## Capabilities

### Modified Capabilities
- `frontend-publish-wizard`: The publish wizard gains an automated SoundCloud quota-recovery flow (confirm once, then delete-and-retry with backoff), replacing the prior delete-then-manually-republish step.

## Impact

- **Frontend**: `frontend/src/components/publish-wizard.tsx` — the publish request is extracted into a reusable classifier; the delete handler now deletes then auto-retries publishing with spaced delays (8s / 15s / 30s) and a "republishing…" progress state.
- **Backend / APIs**: None. Reuses the existing `POST .../publish/{target}` (413 + oldest track) and `DELETE .../oauth/soundcloud/tracks/{id}` endpoints unchanged.
- **Dependencies**: None new.
