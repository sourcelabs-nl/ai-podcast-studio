## MODIFIED Requirements

### Requirement: Generation waits for the pollers to cover the window
Generation SHALL be deferred while any enabled source of the podcast is behind the window: a source
is behind when it has never polled, when its last poll failed, or when its last poll happened
longer than its own poll interval before the window end. Before deferring, the scheduler SHALL poll
every behind source whose last poll did not fail immediately, without waiting for that source's
regular poll interval, and SHALL then re-check coverage in the same tick. A source whose last poll
failed SHALL NOT be polled early, so its backoff is respected. A source SHALL never be polled by two
paths at the same time, and a source that was polled after the catch-up was requested SHALL NOT be
polled again by it. A deferral SHALL create no episode and SHALL touch no state, so the slot stays
due and the next scheduler tick re-checks. The wait SHALL be bounded by
`app.episode.poll-coverage-deadline-minutes` measured from the slot, after which the episode SHALL
be generated anyway and every source still behind SHALL be named at WARN.

#### Scenario: Behind sources are polled immediately at the slot
- **WHEN** the slot arrives and two healthy sources last polled before the window end minus their interval
- **THEN** both are polled right away and, when both polls succeed, the episode is generated in the same tick

#### Scenario: A source in backoff is not polled early
- **WHEN** the slot arrives and a behind source's last poll failed
- **THEN** that source is not polled by the catch-up and keeps its regular backoff schedule

#### Scenario: A failed catch-up poll defers generation
- **WHEN** a catch-up poll of a behind source fails before the deadline
- **THEN** that source counts as behind, no episode is created, and later ticks do not poll it again early

#### Scenario: A source is not polled twice concurrently
- **WHEN** the regular polling loop is polling a source while the slot catch-up requests the same source
- **THEN** the catch-up waits for that poll, sees it completed after the request, and does not poll the source again

#### Scenario: A source that has not polled for hours defers generation
- **WHEN** the slot arrives, one enabled source last polled successfully eight hours ago, and its catch-up poll fails
- **THEN** no episode is created and the deferral is logged with that source named

#### Scenario: A source polled within its own interval does not defer
- **WHEN** a source with a 30-minute interval last polled successfully 10 minutes before the window end
- **THEN** it counts as covering the window, is not polled by the catch-up, and generation proceeds

#### Scenario: A failed last poll counts as behind
- **WHEN** a source polled a minute before the window end but that poll failed
- **THEN** it counts as behind, because the poll timestamp is written on failure too

#### Scenario: Generation proceeds after the deadline
- **WHEN** a source is still behind and the coverage deadline has passed
- **THEN** the episode is generated and the sources still behind are named at WARN

#### Scenario: A disabled source never defers generation
- **WHEN** a disabled source has never polled
- **THEN** it is ignored by the coverage check and by the catch-up
