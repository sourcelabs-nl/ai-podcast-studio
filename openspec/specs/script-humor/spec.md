# script-humor Specification

## Purpose
TBD - created by archiving change rework-humor-prompt-block. Update Purpose after archive.

## Requirements

### Requirement: Shared humor-and-tone prompt block
The system SHALL provide a shared `buildHumorBlock()` function in `ComposerUtils.kt` that produces a `HUMOR & TONE` engagement rule included in every compose-stage prompt (`BriefingComposer`, `DialogueComposer`, `InterviewComposer`). The block SHALL be interpolated as the FIRST bullet of the "Engagement techniques" section, and SHALL NOT be attached to the sign-off instruction. The rule SHALL be concrete and countable: it SHALL require 2-3 genuine moments of humor per episode, each tied to a specific story (never generic filler), and SHALL frame this as a hard requirement on par with the interruption count. The rule SHALL provide a speaker-neutral flavour menu (absurd or everyday comparison, playful exaggeration, self-deprecating aside about the hosts or the AI field, deadpan one-liner) and SHALL instruct varying the flavour across the episode. The rule SHALL establish the overall vibe as relaxed and playful (colleagues who enjoy the subject, not news anchors).

`buildHumorBlock` SHALL take a `multiSpeaker` flag. When it is set, the rule SHALL additionally require that at least one beat comes from a speaker other than the one who opens the episode, and that at least one beat is a direct reaction to what the other speaker just said rather than a prepared aside dropped into a turn. `InterviewComposer` and `DialogueComposer` SHALL set it; `BriefingComposer` SHALL NOT, having a single voice with no other speaker to react to.

The flavour menu describes shapes of line, so on its own it yields delivered lines from whichever speaker the model has made the funny one. Episode 208 met the count with three beats that were all the interviewer's and all asides. The added requirements constrain who speaks and what the joke answers to, which the flavour menu does not.

#### Scenario: Humor block is the first engagement bullet
- **WHEN** any of the three composers builds its prompt
- **THEN** the `HUMOR & TONE` rule appears as the first bullet under "Engagement techniques", before the history-check and deep-dive instructions

#### Scenario: Humor block is not attached to the sign-off
- **WHEN** any of the three composers builds its prompt
- **THEN** the SIGN-OFF bullet contains only sign-off guidance and no humor or tone instructions

#### Scenario: Countable humor requirement
- **WHEN** the humor block is rendered
- **THEN** it requires 2-3 genuine humor moments per episode, each tied to a specific story, framed as a hard requirement

#### Scenario: Two-speaker formats require shared humor
- **WHEN** `InterviewComposer` or `DialogueComposer` builds its prompt
- **THEN** the humor rule requires at least one beat from a speaker other than the opener, and at least one beat that reacts to the other speaker's previous line

#### Scenario: A monologue format is unaffected
- **WHEN** `BriefingComposer` builds its prompt
- **THEN** the humor rule contains no requirement about another speaker

### Requirement: Joke hygiene
The humor rule SHALL instruct that each joke lands in one or two sentences and the script moves on, SHALL forbid explaining the joke or letting it derail a segment, and SHALL keep humor away from genuinely serious or negative stories.

#### Scenario: Jokes are brief and not explained
- **WHEN** the humor block is rendered
- **THEN** it instructs landing each joke in one or two sentences without explaining it

#### Scenario: Serious stories excluded from humor
- **WHEN** the humor block is rendered
- **THEN** it instructs keeping humor away from genuinely serious or negative stories

### Requirement: Friday boost
On Fridays (server-local date via `LocalDate.now()`), the humor block SHALL append an extra instruction requesting one additional humorous beat and a notch higher energy. The instruction SHALL allow acknowledging the end of the week only conversationally and in passing (e.g. "It's the end of the week...", "What a week..."), and SHALL forbid direct greetings or shout-outs such as "Happy Friday". On all other days, no Friday-specific text SHALL be included.

#### Scenario: Friday extra beat included
- **WHEN** a script is composed on a Friday
- **THEN** the humor block contains the Friday instruction requesting one extra humorous beat and higher energy

#### Scenario: No Happy Friday shout-out
- **WHEN** a script is composed on a Friday
- **THEN** the humor block forbids direct end-of-week greetings or shout-outs like "Happy Friday" and only permits conversational, in-passing acknowledgements

#### Scenario: No Friday text on other days
- **WHEN** a script is composed on a Monday
- **THEN** the humor block contains no Friday-specific instruction
