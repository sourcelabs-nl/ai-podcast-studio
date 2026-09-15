## ADDED Requirements

### Requirement: Consecutive same-speaker turns are merged
The system SHALL join two or more adjacent speaker turns carrying the same role into a single turn of that role, preserving the text of each in order, and SHALL log at WARN that it did so and how many turns it merged.

The merge SHALL run after an unclosed final turn is closed and before text outside the speaker tags is stripped, so a turn recovered by an earlier step takes part in the check.

Merging SHALL apply only to roles the podcast itself uses, and only where the turns are separated by whitespace alone. A tag outside the role set SHALL be left for role validation, and any other content between two turns SHALL leave both turns untouched.

#### Scenario: Two interviewer turns in a row
- **WHEN** a script contains two consecutive `<interviewer>` turns separated by a newline
- **THEN** the script contains one `<interviewer>` turn carrying both bodies in order, and the merge is logged

#### Scenario: A correctly alternating script
- **WHEN** no two adjacent turns share a role
- **THEN** the script is returned unchanged and nothing is logged

#### Scenario: A run of three turns by one speaker
- **WHEN** three consecutive turns carry the same role
- **THEN** they become a single turn of that role

#### Scenario: A tag outside the podcast's roles
- **WHEN** two adjacent turns carry a role the podcast does not use
- **THEN** they are left as they are, so role validation still sees them

#### Scenario: Text between two same-speaker turns
- **WHEN** two turns of the same role are separated by anything other than whitespace
- **THEN** neither turn is altered
