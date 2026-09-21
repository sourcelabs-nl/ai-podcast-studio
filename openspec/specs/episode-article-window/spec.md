# episode-article-window Specification

## Purpose
TBD - created by archiving change episode-owns-its-article-window. Update Purpose after archive.

## Requirements

### Requirement: An episode owns the article window it covers
Every episode SHALL record the article window it was generated for as `window_start` and
`window_end`, written once when the episode is created and never recomputed. The window is
half-open: an article belongs to it when its publication instant is at or after `window_start` and
strictly before `window_end`. Every stage that selects articles for that episode SHALL use the
stored window, so a retry, a re-run and a regeneration all select from the period the run started
with rather than from the period that happens to be current. Episodes generated before the window
was recorded SHALL read as having no window.

#### Scenario: The window is persisted when the episode is created
- **WHEN** a run creates its GENERATING episode
- **THEN** the episode row carries the `window_start` and `window_end` of that run

#### Scenario: The window is not recomputed later in the run
- **WHEN** the same run reaches article selection minutes after the episode was created
- **THEN** it selects on the window stored on the episode, not on a window derived from the current time

#### Scenario: An episode from before the migration has no window
- **WHEN** an episode written before `window_start` and `window_end` existed is read
- **THEN** both are null and the episode is not treated as having covered any period

### Requirement: The window spans the podcast's previous scheduled slot to the slot being served
The window SHALL end at the scheduled slot being served, not at the moment the run happens to
start, so a run that begins late still covers the period its slot stands for. The window SHALL
start at the latest slot of the podcast's cron strictly before that end, evaluated in the podcast's
timezone. An unparseable cron or timezone SHALL fall back to the cap and to UTC respectively rather
than failing the run.

#### Scenario: A weekday slot covers the previous 24 hours
- **WHEN** a podcast with cron `0 0 15 * * MON-FRI` in Europe/Amsterdam generates for the Tuesday 15:00 slot
- **THEN** the window runs from Monday 15:00 to Tuesday 15:00 local time

#### Scenario: A Monday slot reaches back across the weekend
- **WHEN** the same podcast generates for the Monday 15:00 slot
- **THEN** the window runs from the previous Friday 15:00 to Monday 15:00 local time

#### Scenario: A late start does not shift the window
- **WHEN** the 15:00 slot is only picked up at 15:05
- **THEN** the window still ends at 15:00

#### Scenario: An ad-hoc run ends its window now
- **WHEN** an episode is generated manually outside any slot
- **THEN** the window ends at the current instant and starts at the latest slot before it

#### Scenario: An unparseable cron falls back to the cap
- **WHEN** the podcast's cron expression cannot be parsed
- **THEN** the window starts at `app.episode.max-window-days` before its end and the failure is logged at WARN

### Requirement: The window extends back over a period no episode covered
When the most recent episode that carries a window and was neither failed nor discarded ends before
the computed window start, the window SHALL start where that coverage ended instead, so the content
of a day whose episode failed or was discarded is not orphaned between two windows. A failed or
discarded episode SHALL NOT count as having covered its window. The extension SHALL be capped at
`app.episode.max-window-days` before the window end.

#### Scenario: A failed day is absorbed by the next window
- **WHEN** Monday's episode failed and Tuesday's slot is served
- **THEN** Tuesday's window starts where the last successful episode's window ended, not at Monday 15:00

#### Scenario: Coverage past the previous slot does not shorten the window
- **WHEN** an earlier run already covered a window ending at or after the computed start
- **THEN** the window starts at the previous slot and is not shortened

#### Scenario: A long idle period is capped
- **WHEN** the last covered window ended five weeks ago and the cap is 7 days
- **THEN** the window starts 7 days before its end and the cap is logged at WARN

### Requirement: Generation waits for the pollers to cover the window
Generation SHALL be deferred while any enabled source of the podcast is behind the window: a source
is behind when it has never polled, when its last poll failed, or when its last poll happened
longer than its own poll interval before the window end. A deferral SHALL create no episode and
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

### Requirement: A past window can be re-run
The system SHALL provide `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/rerun`,
which runs the stored window of a failed or discarded episode as a fresh episode and returns that
episode immediately while the pipeline runs in the background. The source episode SHALL be left
untouched. The re-run SHALL NOT update the podcast's `last_generated_at`, so re-running a past
window does not satisfy the current slot. An episode in any other status SHALL be rejected with
409, as SHALL an episode that carries no window.

#### Scenario: A discarded day is re-run
- **WHEN** a discarded episode carrying a window is re-run
- **THEN** a new GENERATING episode is created with the same window and the pipeline runs for it

#### Scenario: The re-run does not satisfy today's slot
- **WHEN** a past window is re-run
- **THEN** the podcast's `last_generated_at` is unchanged

#### Scenario: A published episode cannot be re-run directly
- **WHEN** a re-run is requested for a GENERATED episode
- **THEN** the request is rejected with 409 and the caller is told to discard it first

#### Scenario: An episode without a window cannot be re-run
- **WHEN** a re-run is requested for an episode from before windows were recorded
- **THEN** the request is rejected with 409 and code `episode_not_rerunnable`
