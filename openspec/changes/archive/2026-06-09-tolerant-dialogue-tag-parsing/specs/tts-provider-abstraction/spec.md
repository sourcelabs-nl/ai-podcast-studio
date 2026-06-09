## ADDED Requirements

### Requirement: Tolerant dialogue script parsing
The system SHALL parse tagged dialogue scripts into ordered speaker turns where each turn's role is taken from its opening tag (`<role>`), and the turn's text runs until the next tag token (opening or closing) or the end of the script. The parser SHALL NOT require the closing tag to match the opening tag. This makes parsing robust to the malformed tags an LLM occasionally emits (a turn closed with a different role's closing tag, or a closing tag omitted before the next opening tag) so that spoken turns are never silently dropped. Text that appears before any opening tag, or after a turn ends and before the next opening tag, SHALL be ignored with a warning.

#### Scenario: Closing tag does not match opening tag
- **WHEN** the script contains `<interviewer>What happened?</interviewer><expert>A lot happened.</interviewer>`
- **THEN** two turns are returned: `(interviewer, "What happened?")` and `(expert, "A lot happened.")`

#### Scenario: Closing tag missing before next opening tag
- **WHEN** the script contains `<interviewer>First question<expert>The answer.</expert>`
- **THEN** two turns are returned: `(interviewer, "First question")` and `(expert, "The answer.")`

#### Scenario: Consecutive turns of the same speaker
- **WHEN** the script contains `<interviewer>One.</interviewer><interviewer>Two.</interviewer>`
- **THEN** two separate `interviewer` turns are returned in order

#### Scenario: Final turn without a closing tag
- **WHEN** the script ends with an opened turn that is never closed, e.g. `<expert>Unterminated answer.`
- **THEN** the final turn is recovered as `(expert, "Unterminated answer.")`

#### Scenario: Text outside speaker tags ignored
- **WHEN** the script contains text before the first opening tag or after a turn closes and before the next opening tag
- **THEN** that text is ignored and a warning is logged
