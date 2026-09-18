## ADDED Requirements

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
