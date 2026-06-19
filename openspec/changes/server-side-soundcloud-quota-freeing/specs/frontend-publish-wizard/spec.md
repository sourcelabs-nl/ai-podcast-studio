## ADDED Requirements

### Requirement: Wizard delegates quota recovery to the server
The publish wizard SHALL NOT orchestrate multi-step backend recovery. It makes one publish request; on an HTTP 413 quota response it presents the server-computed `tracksToDelete` plan for one-time user consent and, on confirmation, makes a single `free-and-publish` request that performs the deletion and publish server-side. The wizard SHALL NOT contain retry/backoff timers, per-track delete calls, or logic that decides which or how many tracks to remove.

#### Scenario: Single consent call, no client retry loop
- **WHEN** publishing returns a 413 quota plan and the user consents to remove the listed tracks
- **THEN** the wizard issues exactly one `free-and-publish` request with the planned track IDs and renders its result (published, a fresh plan, or an error), without looping or deleting tracks itself

#### Scenario: No client-side quota arithmetic
- **WHEN** the wizard receives a quota plan
- **THEN** it displays the server's `tracksToDelete` and `secondsToFree` as-is and does not compute or adjust the deletion set
