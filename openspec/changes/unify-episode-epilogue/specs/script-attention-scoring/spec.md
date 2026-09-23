## MODIFIED Requirements

### Requirement: Scoring is reached through the API
Triggering a scoring run and reading scores back SHALL be available as REST endpoints, so scores are never obtained by querying the database directly. The controller SHALL validate input and delegate to the scoring service without holding logic of its own.

Scoring SHALL skip an episode that already has a score at the current scorer version for the script the episode currently holds, so re-running over the archive costs nothing for work already done. When an episode's script has been rewritten since its existing score at the current scorer version was recorded, that score no longer describes the episode's current script and SHALL be replaced by a fresh judge call rather than kept.

#### Scenario: Already-scored episodes are skipped
- **WHEN** a scoring run covers an episode already scored at the current version for its current script
- **THEN** no model call is made for that episode

#### Scenario: A rewritten script invalidates its existing score
- **WHEN** an episode's script is rewritten (for example by a feedback recompose, a retry, or a regeneration) after it was scored at the current scorer version
- **THEN** the epilogue judges the new script and the episode's stored score is replaced rather than left describing the old script

#### Scenario: Scores are readable over HTTP
- **WHEN** a client asks for an episode's score
- **THEN** the API returns it without the client touching the database
