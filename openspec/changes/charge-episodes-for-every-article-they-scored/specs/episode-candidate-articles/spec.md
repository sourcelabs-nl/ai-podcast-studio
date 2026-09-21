## ADDED Requirements

### Requirement: An episode records every article that stood as a candidate for it
The system SHALL record, for each episode, every article that was scored as a
candidate for that episode, together with what became of that candidate: it
reached the script, it was excluded by the already-covered gate, it was dropped
as a duplicate by the clustering call, or it was cut by the compose cap.

An article SHALL appear at most once per episode. Candidacy is decided by the
episode's window, and `EpisodeWindowResolver` starts each window where the
previous one ended, so an article falls inside exactly one window and can stand
as a candidate for exactly one episode.

The record SHALL be written in the same transaction that stores the episode's
article links, so an episode can never hold links without the candidacy that
produced them.

#### Scenario: Candidates recorded with their outcomes
- **WHEN** an episode is generated from 188 candidates of which 121 reach the script
- **THEN** 188 candidate records are written for that episode, 121 marked as used
  and the remaining 67 marked with the reason they were dropped

#### Scenario: A gate exclusion is distinguishable from a duplicate
- **WHEN** the already-covered gate excludes an article and the clustering call
  drops another as a duplicate
- **THEN** the two records carry different outcomes

#### Scenario: An article stands as a candidate for one episode only
- **WHEN** an article was recorded as a dropped candidate for an episode
- **THEN** it is not recorded as a candidate for any later episode, because it
  falls outside every later window

### Requirement: Candidate records are separate from the episode's article links
The candidate record SHALL be held in its own table, not as columns on the
existing episode-article link. Those links define which articles an episode
covers and are read by the show notes, the sources file, the recap and the
marking of articles as processed; a dropped candidate must not reach any of
them.

#### Scenario: A dropped candidate does not become episode content
- **WHEN** an episode has dropped candidates recorded
- **THEN** the show notes, the sources file, the recap and the processed marking
  see only the articles linked to the episode, unchanged

### Requirement: An episode's candidates are readable
The system SHALL expose an episode's candidate records for reading, grouped by
outcome, so the cost of the candidates that did not reach the script can be
computed from the articles they name.

#### Scenario: Reading the candidates of an episode
- **WHEN** the candidates of a named episode are requested
- **THEN** each record is reported with its article and its outcome
