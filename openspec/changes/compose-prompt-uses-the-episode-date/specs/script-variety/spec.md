## MODIFIED Requirements

### Requirement: Per-episode prompt variety rotation

The compose stage SHALL deterministically rotate a set of prompt-shape dimensions per episode so that consecutive episodes from the same podcast do not produce byte-identical scaffolding. Selection MUST be a pure function of `(podcastId, episodeDate)` so that regenerating the same episode yields the same selection. `episodeDate` is the day the episode is about, which is the date its article window ends read in the podcast's timezone, never the date the run happens: two different days re-run on the same afternoon must not share their scaffolding, and the same day re-run later must keep the scaffolding it had.

The rotated dimensions MUST include all six of:

- **Opening style** (e.g. cold-open question, shocking-stat, scene-set, contrarian take, first-person hook).
- **Transition vocabulary set** (e.g. signposts A, signposts B, signposts C).
- **Sign-off shape** (e.g. recap-first, forward-look, call-to-action, quote-of-the-day).
- **Teaser shape** (interview-only; e.g. lead-with-question, curiosity-list, cold-tease, rhetorical-hook).
- **Topic-entry pattern** (interview/dialogue; e.g. straight-question, theme-bridge, contrast-pivot, micro-recap-then-pivot).
- **Penultimate-exchange shape** (interview/dialogue; e.g. mutual-thanks, forward-look-handoff, single-sentence-callback, cold-handoff-to-sign-off).

Briefing/casual/deep-dive/executive-summary composers SHALL consume the first three dimensions; interview and dialogue composers SHALL consume all six.

#### Scenario: Same episode regenerated gets the same selection

- **WHEN** the compose stage runs twice for the same `(podcastId, episodeDate)`
- **THEN** the variety picker returns identical selections on all six dimensions both times

#### Scenario: Two past days re-run on the same afternoon keep distinct scaffolding

- **WHEN** the windows of two different days are re-run within the same hour
- **THEN** each run is keyed on its own episode date, so the selections are those of the two days rather than one shared selection

#### Scenario: Consecutive days produce different selections

- **WHEN** the compose stage runs for the same podcast on five consecutive dates
- **THEN** at least three of the five episodes have a distinct opening-style selection AND at least three have a distinct sign-off-shape selection

#### Scenario: Interview style consumes all six axes

- **WHEN** the interview composer builds a prompt
- **THEN** the prompt content reflects the picker's teaser-shape, topic-entry-pattern, and penultimate-exchange-shape selections in addition to the three shared axes
