# compose-episode-date Specification

## Purpose
Which day a compose-stage prompt is about: how that day is derived from the episode's article
window, and which run paths carry it.

## Requirements

### Requirement: A compose-stage prompt is about the episode's own day
The compose stage SHALL be given the day the episode is about, and every date-dependent part of the
prompt SHALL be derived from that day rather than from the day the run happens. The episode's day is
the date on which its article window ends, read in the podcast's timezone; a podcast whose timezone
is unparseable falls back to UTC, as everywhere else the window is read.

#### Scenario: A re-run of a past window announces that window's day
- **WHEN** the stored window of Wednesday's episode is re-run on Friday
- **THEN** the prompt states Wednesday's date, and today's date appears nowhere in it

#### Scenario: A scheduled run announces the slot it serves
- **WHEN** a scheduled run composes the episode for the slot it is serving
- **THEN** the prompt states the date of that slot

#### Scenario: The podcast's timezone decides the day
- **WHEN** a window ends at an instant that falls on different dates in UTC and in the podcast's
  timezone
- **THEN** the date in the prompt is the one in the podcast's timezone

### Requirement: The Friday beat follows the episode's weekday
The extra end-of-week humor beat SHALL be requested when the episode's own day is a Friday, not when
the run happens on a Friday.

#### Scenario: A Wednesday episode re-run on Friday gets no Friday beat
- **WHEN** Wednesday's window is re-run on a Friday
- **THEN** the prompt does not ask for the end-of-week beat

#### Scenario: A Friday episode re-run later still gets the Friday beat
- **WHEN** Friday's window is re-run on the following Monday
- **THEN** the prompt asks for the end-of-week beat

### Requirement: Every compose path carries the episode's day
A scheduled generation, a manual generation, a full-pipeline retry, a retry resuming at compose, a
re-run of a stored window and a regeneration SHALL all compose with the day of the window they are
serving, taken from the episode being reproduced where one exists. A preview serves no stored
episode and SHALL use the window that ends at the current instant.

#### Scenario: A retry resuming at compose uses the episode's window
- **WHEN** an episode that failed after article selection is retried the next morning
- **THEN** the recomposed script states the day of the window stored on that episode

#### Scenario: A regeneration keeps the day it recomposes
- **WHEN** an episode is regenerated days after it was generated
- **THEN** the regenerated script states the day of the source episode's window

#### Scenario: An episode without a stored window falls back to now
- **WHEN** an episode from before windows were recorded is retried or regenerated
- **THEN** the run resolves a window ending now and composes with that day

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
