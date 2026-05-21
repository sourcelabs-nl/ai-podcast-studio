## MODIFIED Requirements

### Requirement: Startup jitter for initial polls
The system SHALL apply a random jitter to sources that have never been polled (`lastPolled` is null) to prevent all sources from polling simultaneously on first boot. The jitter SHALL be implemented by setting `lastPolled` to a synthetic timestamp of `now - random(0..pollIntervalMinutes) minutes`, so the normal due-check logic naturally staggers initial polls across the first interval window. Once set, this synthetic timestamp persists in the database so subsequent restarts do not re-apply jitter.

Jitter SHALL be applied only on the first polling cycle after `ApplicationReadyEvent`. Sources added at runtime (after the first cycle) SHALL NOT receive jitter; their `lastPolled = null` state SHALL cause them to be treated as due on the next polling cycle, so a newly-added source is polled within one cycle (~60 seconds) of being created.

#### Scenario: First boot with 5 sources at 60-minute interval
- **WHEN** the application starts for the first time with 5 sources that all have `lastPolled = null` and `pollIntervalMinutes = 60`
- **THEN** each source receives a random synthetic `lastPolled` timestamp between 0 and 60 minutes ago, causing them to become due at different times within the first 60 minutes

#### Scenario: Restart does not re-apply jitter
- **WHEN** the application restarts and sources already have `lastPolled` timestamps from a previous run
- **THEN** no jitter is applied, sources use their existing `lastPolled` timestamps

#### Scenario: Source added at runtime is polled on next cycle
- **WHEN** a source is added with `lastPolled = null` after the first polling cycle has already run
- **THEN** no jitter is applied to it, and it is treated as due and polled on the next polling cycle (~60 seconds later)
