## MODIFIED Requirements

### Requirement: Tagged scripts are trimmed to the speaker-tag span
For dialogue and interview compositions, the system SHALL strip any text before the first opening speaker tag and after the last closing speaker tag from the composed script before it is stored. The stripping SHALL preserve all speaker turns (including a final turn whose tag differs from the first turn's tag) and SHALL preserve any text between turns. Scripts containing no speaker tags SHALL be returned unchanged.

Before trimming, the system SHALL repair a speaker turn whose opening delimiter uses square brackets while its closing tag uses angle brackets (`[expert] … </expert>`), rewriting the opener to `<expert>`. Only the podcast's configured speaker roles SHALL be considered, and an opener SHALL be rewritten only when the next `<` character after it begins that role's own closing tag. A speaker turn's body contains no tags, so that condition distinguishes a mis-typed opener from a delivery cue such as `[warm and conversational]`, and prevents a rewrite whose closing tag actually belongs to a later, well-formed turn. Each repair SHALL be logged at WARN.

Without this repair such a turn is invisible to the speaker-tag pattern, so trimming reads it as text sitting before the script and deletes it, and speaker-tag validation sees no tag to reject. Episode 184 lost its entire cold open and introduction that way, on a turn the model had otherwise written correctly.

Trimming SHALL log at WARN when the text it discards is more than the short meta-commentary it exists to remove: either longer than 200 characters, or containing a closing tag. A discarded run containing a closing tag means a malformed turn was thrown away, and the log message SHALL say so. Discarding spoken content SHALL NOT be silent.

#### Scenario: Leading LLM preamble removed
- **WHEN** the compose LLM returns "I have enough context. Writing the script now." followed by tagged speaker turns
- **THEN** the stored script starts at the first opening speaker tag

#### Scenario: Trailing text after last turn removed
- **WHEN** the composed script ends with prose after the final closing tag
- **THEN** the stored script ends at the last closing speaker tag

#### Scenario: Final turn with different tag preserved
- **WHEN** the script starts with an `<interviewer>` turn and ends with an `<expert>` turn
- **THEN** the trailing `<expert>` turn is preserved in the stored script

#### Scenario: Untagged script passes through
- **WHEN** the composed script contains no speaker tags
- **THEN** the script is returned unchanged

#### Scenario: Square-bracketed opening turn recovered
- **WHEN** the compose LLM returns `[interviewer]Welcome to the show.</interviewer>` followed by well-formed turns
- **THEN** the opener is rewritten to `<interviewer>`, the turn is preserved in the stored script, and a WARN records the recovery

#### Scenario: Delivery cue inside a turn is not mistaken for a tag
- **WHEN** a turn reads `<expert>[warm and conversational] Hello there.</expert>`
- **THEN** the script is unchanged

#### Scenario: Square-bracketed role outside the configured set is left alone
- **WHEN** the script contains `[narrator]Once upon a time.</narrator>` and the podcast's roles are `interviewer` and `expert`
- **THEN** the script is unchanged

#### Scenario: Opener whose closing tag belongs to a later turn is left alone
- **WHEN** the script reads `[expert] unterminated <interviewer>Question?</interviewer><expert>Answer.</expert>`
- **THEN** the script is unchanged, because rewriting the opener would nest one turn inside another

#### Scenario: Discarding a malformed turn is logged
- **WHEN** trimming discards a run of text that contains a closing speaker tag
- **THEN** a WARN records the discarded length and states that a malformed turn was dropped

#### Scenario: Routine preamble is discarded without a warning
- **WHEN** trimming discards fewer than 200 characters of text containing no closing tag
- **THEN** no warning is logged
