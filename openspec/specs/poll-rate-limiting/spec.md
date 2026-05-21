# Capability: Poll Rate Limiting

## Purpose

Configurable per-host and per-source-type request spacing to prevent rate limit violations on free/community-run services, with parallel host-grouped polling and startup jitter.
## Requirements
### Requirement: Per-host poll delay
The system SHALL insert a configurable delay between consecutive poll requests to the same host. When multiple sources share the same URL host, they SHALL be polled sequentially within their host group with `kotlinx.coroutines.delay()` applied after each poll. The delay duration SHALL be resolved using the following precedence chain:
1. `source.pollDelaySeconds` — explicit per-source override (nullable integer on Source entity)
2. `app.source.host-overrides.<host>.poll-delay-seconds` — host-specific configuration
3. `app.source.poll-delay-seconds.<type>` — source-type default configuration
4. `0` — no delay (fallback)

#### Scenario: Two Nitter sources on same host with host override
- **WHEN** two RSS sources both have host `nitter.net` and `app.source.host-overrides.nitter.net.poll-delay-seconds` is `3`
- **THEN** the second source is polled at least 3 seconds after the first source completes

#### Scenario: Per-source override takes precedence
- **WHEN** a source has `pollDelaySeconds = 5` and `app.source.host-overrides.nitter.net.poll-delay-seconds` is `3`
- **THEN** a 5-second delay is applied after polling that source (per-source wins)

#### Scenario: Type default applies when no override exists
- **WHEN** a website source has no `pollDelaySeconds` and its host has no host override, and `app.source.poll-delay-seconds.website` is `2`
- **THEN** a 2-second delay is applied after polling that source

#### Scenario: No delay when nothing configured
- **WHEN** a source has no `pollDelaySeconds`, its host has no override, and no type default exists
- **THEN** no delay is applied (0 seconds)

### Requirement: Parallel host group polling
The system SHALL group all due sources by their URL host and poll each host group in parallel using Kotlin coroutines. Host groups SHALL run concurrently under `supervisorScope` so that a failure in one host group does not cancel polling of other host groups. Within each host group, sources SHALL be polled sequentially with the configured delay between them.

#### Scenario: Two host groups polled in parallel
- **WHEN** there are 3 due sources on `nitter.net` and 2 due sources on `reddit.com`
- **THEN** both host groups are polled concurrently — `reddit.com` sources do not wait for `nitter.net` sources to finish

#### Scenario: One host group fails without affecting others
- **WHEN** polling a source on `nitter.net` throws an unexpected exception that escapes the poller's error handling
- **THEN** the `reddit.com` host group continues polling normally and completes independently

#### Scenario: Sources with unparseable URLs grouped together
- **WHEN** a source has a URL that cannot be parsed to extract a host
- **THEN** the source is placed in a default group and polled without delay

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

### Requirement: Host-specific delay configuration
The system SHALL support configuring poll delays per host via `app.source.host-overrides.<host>.poll-delay-seconds` in application configuration. This allows operators to set appropriate delays for known rate-limited hosts without modifying individual source records.

#### Scenario: Nitter host override configured
- **WHEN** `app.source.host-overrides.nitter.net.poll-delay-seconds` is set to `3`
- **THEN** all sources with host `nitter.net` use a 3-second delay between polls (unless overridden per-source)

#### Scenario: Multiple host overrides
- **WHEN** host overrides are configured for both `nitter.net` (3s) and `nitter.privacydev.net` (5s)
- **THEN** sources are delayed according to their respective host configuration

### Requirement: Source-type default delay configuration
The system SHALL support configuring default poll delays per source type via `app.source.poll-delay-seconds.<type>` in application configuration. This provides a baseline delay for all sources of a given type.

#### Scenario: Website type default
- **WHEN** `app.source.poll-delay-seconds.website` is set to `2` and a website source has no per-source or host override
- **THEN** a 2-second delay is applied after polling that source

#### Scenario: RSS type default of zero
- **WHEN** `app.source.poll-delay-seconds.rss` is set to `0`
- **THEN** no delay is applied after polling RSS sources (unless overridden)

