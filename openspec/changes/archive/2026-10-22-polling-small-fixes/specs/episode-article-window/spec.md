## MODIFIED Requirements

### Requirement: Generation waits for the pollers to cover the window
Generation SHALL be deferred while any enabled source of the podcast is behind the window: a source
is behind when it has never polled, when its last poll failed, or when its last poll happened
longer than its own poll interval before the window end. A deferral SHALL create no episode and
SHALL touch no state, so the slot stays due and the next scheduler tick re-checks. The wait SHALL
be bounded by `app.episode.poll-coverage-deadline-minutes` measured from the slot, after which the
episode SHALL be generated anyway and every source still behind SHALL be named at WARN. A slot's
due and deferral log lines SHALL be written at info the first time the slot is checked and at debug
on each later re-check of the same slot.

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

#### Scenario: A deferred slot is logged at info once
- **WHEN** a deferred slot is re-checked every minute
- **THEN** its deferral is logged at info on the first check and at debug on every later one
