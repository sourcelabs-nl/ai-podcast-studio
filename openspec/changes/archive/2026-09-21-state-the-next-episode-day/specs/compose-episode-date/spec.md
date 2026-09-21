## ADDED Requirements

### Requirement: A compose-stage prompt states when the show is next on air
The compose stage SHALL be given the day the podcast is next scheduled to publish after the
episode's own day, derived from the podcast's cron read in the podcast's timezone. The prompt SHALL
express that day relative to the episode's own day: "tomorrow" when it is the following day, and the
weekday name otherwise.

A cron firing several times on one day SHALL yield the next day carrying a slot, not the episode's
own day again.

#### Scenario: A weekday show mid-week is back tomorrow
- **WHEN** a podcast publishing Monday to Friday composes its Wednesday episode
- **THEN** the prompt states that the next episode is tomorrow

#### Scenario: A weekday show on Friday is back on Monday
- **WHEN** a podcast publishing Monday to Friday composes its Friday episode
- **THEN** the prompt states that the next episode is Monday

#### Scenario: A podcast publishing twice a day
- **WHEN** a podcast whose cron fires twice on each weekday composes an episode
- **THEN** the prompt names the following publishing day, not the episode's own day

### Requirement: The sign-off promises the scheduled day or promises nothing
The prompt SHALL instruct that a sign-off referring to the next episode name only the scheduled day,
SHALL forbid naming any other interval, and SHALL state that making no such promise is acceptable.

A sign-off that says nothing about the next episode is correct. A sign-off naming a day or interval
other than the scheduled one is not.

#### Scenario: A sign-off that promises a return
- **WHEN** the script's sign-off says when the show will be back
- **THEN** it names the scheduled day

#### Scenario: A sign-off that promises nothing
- **WHEN** the script's sign-off ends without referring to the next episode
- **THEN** the script is correct, and nothing in the prompt requires a promise to be added

### Requirement: An unknown schedule adds no directive
When the next publishing day cannot be determined, the prompt SHALL omit the directive entirely
rather than state a default or a guessed cadence.

The next day cannot be determined when the podcast's cron is unparseable, or when it fires no slot
within the resolver's forward search horizon. Both cases SHALL be logged.

#### Scenario: An unparseable cron
- **WHEN** a podcast's cron cannot be parsed
- **THEN** the prompt says nothing about when the show is next on air, and a warning is logged

#### Scenario: A podcast publishing less often than the search horizon
- **WHEN** a podcast's cron fires no slot within the forward search horizon after the episode's day
- **THEN** the prompt says nothing about when the show is next on air, and a warning is logged

### Requirement: The next day follows the episode's day on every compose path
The next publishing day SHALL be resolved from the episode's own day, so that a re-run, a retry
resuming at compose, a regeneration and a preview each promise what was true for the window they
serve rather than what is true on the day the run happens.

#### Scenario: A regeneration of a past episode
- **WHEN** a Friday episode is regenerated on the following Wednesday
- **THEN** the prompt states that the next episode is Monday, the day that followed the episode's own
  window
