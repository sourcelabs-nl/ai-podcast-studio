# compose-script-cleanup Specification

## Purpose
TBD - created by archiving change strip-compose-preamble. Update Purpose after archive.

## Requirements

### Requirement: Tagged scripts are trimmed to the speaker-tag span
For dialogue and interview compositions, the system SHALL strip any text before the first opening speaker tag and after the last closing speaker tag from the composed script before it is stored. The stripping SHALL preserve all speaker turns (including a final turn whose tag differs from the first turn's tag) and SHALL preserve any text between turns. Scripts containing no speaker tags SHALL be returned unchanged.

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

### Requirement: Monologue scripts drop a leading meta-commentary paragraph
For briefing (monologue) compositions, the system SHALL remove the first paragraph of the composed script only when it is at most 300 characters AND matches known LLM meta-commentary phrasings (writing/drafting/composing the script, having enough/plenty of context or information, "what I need"). Genuine spoken openings SHALL never be removed; when in doubt the paragraph SHALL be kept.

#### Scenario: Writing-the-script preamble removed
- **WHEN** a briefing script starts with "I have plenty of context now. Let me write the script." followed by the spoken opening
- **THEN** the stored script starts at the spoken opening

#### Scenario: Genuine opening kept
- **WHEN** a briefing script opens with a spoken hook (e.g., "What happens when one AI commands a thousand others?")
- **THEN** the first paragraph is kept unchanged

#### Scenario: Long first paragraph kept despite keyword
- **WHEN** the first paragraph exceeds 300 characters and merely mentions the word "script"
- **THEN** the paragraph is kept unchanged

### Requirement: Invalid speaker tags are rejected before storage with a self-correcting retry
For dialogue and interview compositions, the system SHALL validate the compose LLM's response before the script is accepted, rejecting it when either:
- a speaker tag is present that is not one of the podcast's allowed roles (the podcast's configured `ttsVoices` keys for dialogue, or `interviewer`/`expert` for interview), or
- the response carries no speaker tag for any allowed role at all.

When a response is rejected, the system SHALL append an error to the LLM request describing the failure and identifying the allowed tags, and re-issue the request, up to 2 retries (3 attempts total). If no attempt produces an acceptable response, the system SHALL fail the compose call with an error describing the failure rather than returning the script.

When a response is rejected for both reasons at once, the system SHALL report the invalid tag(s), since naming the offending tag gives the model more to correct than reporting the absence of tags.

For the purpose of the no-speaker-tag check, a tag SHALL be considered present when an opening tag for an allowed role appears with either angle-bracket (`<expert>`) or square-bracket (`[expert]`) delimiters, whether or not a matching closing tag follows. Both forms are recovered downstream, so rejecting them would re-run a compose the pipeline can already voice.

This validation SHALL apply only to multi-speaker compositions. Briefing (monologue) scripts are a single voice and legitimately carry no speaker tags.

#### Scenario: Leaked tool-call artifact rejected and self-corrected
- **WHEN** the compose LLM's first response contains a `<function_results>` tag alongside valid `<interviewer>`/`<expert>` tags
- **THEN** the system re-prompts the model with an error naming `function_results` as invalid and `interviewer`/`expert` as the allowed tags
- **AND** if the model's next response contains only valid tags, that response is accepted

#### Scenario: Untagged script rejected and self-corrected
- **WHEN** the compose LLM's first response is a well-formed alternating dialogue containing no speaker tag at all
- **THEN** the system re-prompts the model with an error stating the response contained no speaker tags and naming the allowed tags
- **AND** if the model's next response contains valid tags, that response is accepted

#### Scenario: Only valid tags present on the first attempt
- **WHEN** the compose LLM's response contains only tags that are in the podcast's allowed role set
- **THEN** the system accepts the response without retrying

#### Scenario: Square-bracketed opener is not treated as untagged
- **WHEN** the compose LLM's response opens a turn with `[interviewer]` and closes it with `</interviewer>`
- **THEN** the system accepts the response without retrying, leaving the downstream normalization to recover the turn

#### Scenario: Model never self-corrects on an invalid tag
- **WHEN** the compose LLM's response contains an invalid tag on every attempt through the retry budget
- **THEN** the system throws an error identifying the invalid tag(s) instead of returning the script

#### Scenario: Model never produces speaker tags
- **WHEN** the compose LLM's response contains no speaker tag on every attempt through the retry budget
- **THEN** the system throws an error stating the script has no speaker tags instead of returning the script

#### Scenario: Response still awaiting tool call resolution
- **WHEN** the compose LLM's response still has pending tool calls
- **THEN** the system does not validate speaker tags on that response, since its text is not the final script

#### Scenario: Model never self-corrects
- **WHEN** the compose LLM's response contains an invalid tag on every attempt through the retry budget
- **THEN** the system throws an error identifying the invalid tag(s) instead of returning the script

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

### Requirement: A mismatched closing speaker tag is repaired

A speaker turn closed with a tag naming a different role than its opener SHALL have that closer
rewritten to match the opener, and each rewrite SHALL be logged at WARN naming both roles.

The turn pattern is `<(\w+)>.*?</\1>` matched across newlines, so a mismatched pair does not fail to
match: the match runs on to the next correctly named closer and swallows every intervening turn into
one. Nothing downstream can see it. The role check reads the role off the opener and finds it valid,
and TTS voices the whole swallowed run as one speaker. Episode 222 lost the speaker attribution of
its entire second half to two wrong closers and one misspelled `</epxert>`.

The opener SHALL be treated as authoritative, because it sits immediately after the previous turn's
closer where the speaker is already established by the alternation, while the closer is the tag the
model demonstrably got wrong.

The repair SHALL run after the square-bracket opener fix and before the unclosed-final-turn fix: the
latter locates the tail after the last turn the pattern can see, which a mismatched closer earlier in
the script would otherwise place wrongly.

Only a closer directly following an opener of a known role SHALL be rewritten. An opener never
closed at all is left to the existing recovery and, failing that, to validation.

#### Scenario: A turn closed with the other role's tag

- **WHEN** a script contains `<expert>…</interviewer>` followed by further well-formed turns
- **THEN** the closer becomes `</expert>`, the following turns remain their own turns, and a WARN
  names both roles

#### Scenario: A misspelled closer

- **WHEN** a script closes an `<expert>` turn with `</epxert>`
- **THEN** the closer becomes `</expert>`

#### Scenario: Delivery markup is left alone

- **WHEN** a turn's body contains markup carrying attributes, such as `<break time="1s" />`
- **THEN** the script is unchanged

### Requirement: A script whose speaker tags do not pair up is rejected

Compose SHALL reject a script in which the speaker tags are not a flat sequence of matched
opener/closer pairs of allowed roles, feeding the specific fault back to the model and retrying, and
failing the episode when the model does not correct it.

This is distinct from the two faults already checked. A tag outside the allowed roles and a script
with no tags at all are both invisible to this fault and it to them: mismatched or wrongly ordered
tags are each individually valid roles, so the role check passes and the presence check passes.

Validation SHALL run on the script as the cleanup will leave it, not on the raw response, so a fault
the repairs already handle does not spend another compose call, the most expensive in the pipeline.

#### Scenario: A repairable fault costs no retry

- **WHEN** the model returns a script whose only fault is a mismatched closer
- **THEN** the response is accepted after repair and the model is called exactly once

#### Scenario: An ambiguous fault is re-prompted

- **WHEN** the model returns a script in which a turn opens while another is still open, so where the
  first turn ended cannot be known
- **THEN** the model is re-prompted with the specific fault named, and the corrected script is used

#### Scenario: A persistent fault fails the episode

- **WHEN** every attempt returns a script whose tags do not pair up
- **THEN** compose raises rather than storing a script whose turns are attributed to the wrong
  speakers

### Requirement: Multi-speaker prompts state that speaker tags are mandatory
The dialogue and interview compose prompts SHALL instruct the model that every line of spoken text, including the cold open and the sign-off, must be wrapped in one of the allowed speaker tags, and that an untagged script cannot be voiced. This rule SHALL be stated ahead of the existing rule about which delimiters a speaker tag uses, and SHALL be defined in one shared place so both prompts carry identical wording.

#### Scenario: Dialogue prompt carries the mandatory-tags rule
- **WHEN** a dialogue compose prompt is built for a podcast
- **THEN** the prompt states that speaker tags are mandatory for every line of spoken text and lists the podcast's allowed tags

#### Scenario: Interview prompt carries the mandatory-tags rule
- **WHEN** an interview compose prompt is built
- **THEN** the prompt states that speaker tags are mandatory for every line of spoken text and lists `interviewer` and `expert` as the allowed tags
