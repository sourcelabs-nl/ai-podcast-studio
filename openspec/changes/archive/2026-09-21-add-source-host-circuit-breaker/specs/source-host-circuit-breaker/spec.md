## ADDED Requirements

### Requirement: One circuit breaker per source host

The system SHALL maintain one Resilience4j circuit breaker per source host, shared by every enabled source whose URL resolves to that host, regardless of which podcast owns them. Breakers SHALL be obtained from the application's `CircuitBreakerRegistry` using the shared `source-host` configuration.

Sharing one breaker across a host's sources is what makes the sliding window fill within a single poll round: a source polled once a day would otherwise take as many days as the window is wide before the breaker could open.

A source whose URL has no parseable host SHALL be polled without a breaker, since a breaker keyed on "no host" would lump unrelated sources together.

#### Scenario: All of a host's sources share one breaker
- **WHEN** three sources on `nitter.net` are polled and each fails permanently
- **THEN** the host's breaker has recorded three failures and is open

#### Scenario: Hosts are isolated from each other
- **WHEN** `nitter.net`'s breaker is open
- **THEN** a source on `simonwillison.net` is polled normally

#### Scenario: Source with an unparseable URL is always polled
- **WHEN** a source's URL cannot be parsed into a host
- **THEN** the source is polled and no breaker is consulted

### Requirement: Permanent failures open the breaker, transient failures are ignored

Poll outcomes SHALL be reported to the breaker as exceptions: `PollFailureException.Permanent` for permanent failures (403/404/410/401/DNS) and `PollFailureException.Transient` for transient ones. The `source-host` configuration SHALL list the permanent type under `record-exceptions` and the transient type under `ignore-exceptions`.

An ignored exception counts as neither a success nor a failure. Merely omitting transient failures from `record-exceptions` SHALL NOT be used, because an unrecorded exception counts as a **success** and could close a breaker on a host that never answered.

`SourcePoller.poll` SHALL return the classified failure (or null on success) so callers can report the outcome without re-reading the source row.

#### Scenario: Permanent failures fill the window and open the breaker
- **WHEN** every source polled on a host returns HTTP 403 until the sliding window is full
- **THEN** the host's breaker is open

#### Scenario: Transient failures never open the breaker
- **WHEN** ten consecutive polls on a host fail with socket timeouts
- **THEN** the host's breaker remains closed and every source continues to be polled

#### Scenario: Transient failures do not mask a permanent failure
- **WHEN** a host's polls interleave permanent failures with transient ones, and the permanent failures alone fill the window
- **THEN** the host's breaker is open, because the transient failures were ignored rather than counted as successes

#### Scenario: Successful polls keep the breaker closed
- **WHEN** a host's polls alternate between permanent failures and successes
- **THEN** the failure rate stays below the threshold and the breaker remains closed

#### Scenario: Poller reports its classification
- **WHEN** a source poll fails with HTTP 403
- **THEN** `poll` returns a `PollFailure.Permanent` and the source row records `lastFailureType = "permanent"`

#### Scenario: Successful poll reports no failure
- **WHEN** a source poll succeeds
- **THEN** `poll` returns null

### Requirement: An open breaker skips polling without issuing a request

When a host's breaker is open, the scheduler SHALL NOT issue a request for that host's sources. The rejected call SHALL be reported as a skip, and the skipped source's failure counters and `lastPolled` SHALL NOT be modified.

The scheduler SHALL emit one aggregate log line per host per round reporting how many sources were skipped, in place of a failure log and stack trace per suppressed source.

#### Scenario: Sources behind an open breaker are not polled
- **WHEN** a host's breaker is open and five of its sources are due
- **THEN** no request is issued for any of them

#### Scenario: Breaker opens partway through a round
- **WHEN** ten sources on a host are due and each poll fails permanently, with a sliding window of three
- **THEN** the first three are polled and the remaining seven are skipped without a request

#### Scenario: Skipped sources keep their state
- **WHEN** a source is skipped because its host's breaker is open and it had `consecutiveFailures = 4`
- **THEN** its `consecutiveFailures` remains 4 and its `lastPolled` is unchanged

#### Scenario: Suppression is logged once per host per round
- **WHEN** a poll round skips 35 sources on one host
- **THEN** a single log line reports the host and the skipped count, with no per-source failure stack traces

### Requirement: Recovery through the half-open state restores the whole host

After `wait-duration-in-open-state` the breaker SHALL permit exactly one call through in its half-open state, rejecting the rest. A successful probe SHALL close the breaker; a failing probe SHALL reopen it.

When a poll succeeds while the host's breaker is not closed, the system SHALL reset `consecutiveFailures` to 0 and `lastFailureType` to null on the host's other enabled sources. Without this the breaker would close while each sibling still sat on its own accumulated backoff, so a recovered host would return over days rather than at its normal interval.

#### Scenario: Successful probe closes the breaker and polling resumes
- **WHEN** a host's breaker is half-open and the permitted probe succeeds
- **THEN** the breaker closes and the host's remaining sources are polled in the same round

#### Scenario: Failing probe reopens the breaker
- **WHEN** a host's breaker is half-open and the permitted probe fails permanently
- **THEN** only that one request was issued, the remaining sources are skipped, and the breaker is open again

#### Scenario: Recovery restores the host's siblings
- **WHEN** a host's breaker is tripped and a poll of one of its sources succeeds
- **THEN** every other enabled source on that host has `consecutiveFailures = 0` and `lastFailureType = null`

#### Scenario: Recovered host polls on its normal interval
- **WHEN** a host has recovered and its sources have `pollIntervalMinutes = 30`
- **THEN** those sources are next due 30 minutes after `lastPolled`, not on the 24-hour backoff cap

#### Scenario: Failed probe leaves siblings untouched
- **WHEN** a host's breaker is tripped and the probe fails
- **THEN** no sibling's failure state is reset

#### Scenario: Success on a closed breaker changes only that source
- **WHEN** a source polls successfully while its host's breaker is closed
- **THEN** only that source's own failure counters are reset

### Requirement: Breaker does not change source enablement

An open breaker SHALL NOT modify the `enabled` or `disabledReason` fields of any source. Suppressing polling and disabling a source remain distinct: the breaker is automatic and self-reversing, while disabling is either a user action or per-source auto-disable after permanent failures.

#### Scenario: Sources behind an open breaker remain enabled
- **WHEN** a host's breaker has been open for a week
- **THEN** every source on that host still has `enabled = true` and `disabledReason = null`

#### Scenario: Per-source auto-disable still applies
- **WHEN** repeated probes push a source to `app.source.max-failures` consecutive permanent failures
- **THEN** that source is auto-disabled with a `disabledReason` as it would be without a breaker

### Requirement: Breaker state exposed on the source API

The source API SHALL report, for each source, whether its host's breaker is currently open, so the dashboard can distinguish "not being polled because its host is down" from "failing on its own". The response SHALL include the host and the number of enabled sources sharing it.

#### Scenario: Source behind an open breaker reports it
- **WHEN** the dashboard requests sources and one host's breaker is open
- **THEN** each source on that host is returned with its breaker state reported as open, along with the host name and sibling count

#### Scenario: Healthy source reports a closed breaker
- **WHEN** the dashboard requests a source whose host's breaker is closed
- **THEN** the source is returned with its breaker state reported as closed

### Requirement: Breaker is configured through Resilience4j

Breaker thresholds SHALL live in `application.yaml` under `resilience4j.circuitbreaker.configs.source-host` rather than in application-specific configuration properties, and breaker state and call metrics SHALL be exposed through the Resilience4j actuator integration.

Breaker state SHALL NOT be persisted. It resets on restart, after which the window refills from the same failures and the breaker reopens within one poll round.

#### Scenario: Thresholds are operational configuration
- **WHEN** an operator changes `sliding-window-size` or `wait-duration-in-open-state`
- **THEN** the breaker's behaviour changes without a code change

#### Scenario: Breaker state resets on restart
- **WHEN** the application restarts while a host's breaker is open
- **THEN** the host is polled again, its window refills from the same permanent failures, and the breaker reopens
