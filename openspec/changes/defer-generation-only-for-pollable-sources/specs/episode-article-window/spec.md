# Spec Delta

## MODIFIED Requirements

### Requirement: Generation waits for the pollers to cover the window
Generation SHALL be deferred while any enabled source of the podcast is behind the window and will be polled again before the coverage deadline: a source
is behind when it has never polled, when its last poll failed, or when its last poll happened
longer than its own poll interval before the window end. A source that is behind but whose next poll,
after its backoff for consecutive failures, falls after the coverage deadline SHALL NOT defer generation, and SHALL be named at WARN when the episode is generated without it. A deferral SHALL create no episode and
SHALL touch no state, so the slot stays due and the next scheduler tick re-checks. The wait SHALL
be bounded by `app.episode.poll-coverage-deadline-minutes` measured from the slot, after which the
episode SHALL be generated anyway and every source still behind SHALL be named at WARN.

#### Scenario: A source that has not polled for hours defers generation
- **WHEN** the slot arrives and one enabled source last polled successfully eight hours ago
- **THEN** no episode is created and the deferral is logged with that source named

#### Scenario: A source polled within its own interval does not defer
- **WHEN** a source with a 30-minute interval last polled successfully 10 minutes before the window end
- **THEN** it counts as covering the window and generation proceeds

#### Scenario: A failed last poll counts as behind
- **WHEN** a source polled a minute before the window end but that poll failed
- **THEN** it counts as behind, because the poll timestamp is written on failure too

#### Scenario: Generation proceeds after the deadline
- **WHEN** a source is still behind and the coverage deadline has passed
- **THEN** the episode is generated and the sources still behind are named at WARN

#### Scenario: A disabled source never defers generation
- **WHEN** a disabled source has never polled
- **THEN** it is ignored by the coverage check

#### Scenario: A source in backoff that cannot poll before the deadline does not defer
- **WHEN** the slot arrives, a source is behind after three failed polls with a 30-minute interval, and its backoff puts its next poll after the coverage deadline
- **THEN** generation starts at the slot and the source is named at WARN as missing from the episode

#### Scenario: A source that will poll again before the deadline still defers
- **WHEN** a source is behind after one failed poll and its next poll falls before the coverage deadline
- **THEN** no episode is created yet and the deferral names that source
