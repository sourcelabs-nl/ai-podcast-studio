## ADDED Requirements

### Requirement: An unclosed final speaker turn is recovered
Before speaker-tag stripping, the system SHALL close a trailing speaker turn whose opening tag names one of the podcast's configured roles and which carries no closing tag, and SHALL log the role and the length of the recovered text.

The strip step keeps only what the speaker-turn pattern matches, so a final turn missing its closing tag is discarded in full, and the episode is synthesised and published a fragment short with nothing to indicate it. Closing the tag is unambiguous when the tail names a known role and contains no further role tags: the turn was written and its closer forgotten.

The system SHALL leave the script unchanged when the tail is empty, does not open with a configured role tag, has an empty body, or contains any further role tag, since none of those is a single unterminated turn.

#### Scenario: The last turn is missing its closing tag
- **WHEN** a script ends with `<interviewer>` followed by the sign-off and no `</interviewer>`
- **THEN** the closing tag is added, the turn survives stripping, and the recovery is logged

#### Scenario: A closed final turn is untouched
- **WHEN** every turn in the script is properly closed
- **THEN** the script is returned unchanged

#### Scenario: Trailing prose without a role tag is left to the strip step
- **WHEN** the text after the last complete turn does not begin with a configured role tag
- **THEN** the script is returned unchanged and the strip step removes that text

#### Scenario: A structurally broken tail is not guessed at
- **WHEN** the text after the last complete turn opens with a role tag and contains another role tag
- **THEN** the script is returned unchanged
