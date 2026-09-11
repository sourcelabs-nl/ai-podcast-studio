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
