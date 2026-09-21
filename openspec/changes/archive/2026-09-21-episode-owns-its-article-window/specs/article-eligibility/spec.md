## ADDED Requirements

### Requirement: Article window filter
The system SHALL exclude candidate articles whose publication instant falls outside the run's
article window, which the caller supplies rather than the eligibility service deriving it. The
window is half-open: an article is kept when its instant is at or after the window start and
strictly before the window end. Publication instants SHALL be compared as instants, not as strings,
because `published_at` is written by several fetchers and an offset form or a differing precision
would order wrongly under a lexicographic comparison. An article whose `published_at` is absent or
unparseable SHALL be kept, so content from sources that publish without a usable date is not
silently lost.

#### Scenario: New source with old content excluded
- **WHEN** a new RSS source is added and its articles have `published_at` dates from last week, and the run's window covers the last 24 hours
- **THEN** none of the old articles are included as candidates

#### Scenario: New source with fresh content included
- **WHEN** a new RSS source is added and it has articles published inside the run's window
- **THEN** those articles are included as candidates

#### Scenario: An article published after the window is excluded
- **WHEN** an article was published after the window end, for instance while a deferred run was waiting
- **THEN** it is not a candidate for this run and stays available to the next window

#### Scenario: The window boundaries are half-open
- **WHEN** one article is published exactly at the window start and another exactly at the window end
- **THEN** the first is a candidate and the second is not

#### Scenario: An offset timestamp is placed by instant
- **WHEN** an article's `published_at` is the same instant as the window start but written with a `+01:00` offset rather than `Z`
- **THEN** it is a candidate, because instants are compared rather than strings

#### Scenario: An article without a usable date is kept
- **WHEN** an article has no `published_at`, or one that cannot be parsed
- **THEN** it is kept as a candidate and the unparseable value is logged at WARN

## REMOVED Requirements

### Requirement: Article age gate
**Reason**: Replaced by the article window filter. The gate derived its own cutoff from the latest
published episode's `generated_at`, which left the boundary implicit and unrecorded; the window is
resolved once per run, stored on the episode and supplied to the eligibility service.
**Migration**: The caller passes the run's window instead of relying on `resolveAgeGateCutoff`,
which is gone.
