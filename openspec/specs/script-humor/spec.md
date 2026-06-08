# script-humor Specification

## Purpose
TBD - created by archiving change rework-humor-prompt-block. Update Purpose after archive.
## Requirements
### Requirement: Shared humor-and-tone prompt block
The system SHALL provide a shared `buildHumorBlock()` function in `ComposerUtils.kt` that produces a `HUMOR & TONE` engagement rule included in every compose-stage prompt (`BriefingComposer`, `DialogueComposer`, `InterviewComposer`). The block SHALL be interpolated as the FIRST bullet of the "Engagement techniques" section, and SHALL NOT be attached to the sign-off instruction. The rule SHALL be concrete and countable: it SHALL require 2-3 genuine moments of humor per episode, each tied to a specific story (never generic filler), and SHALL frame this as a hard requirement on par with the interruption count. The rule SHALL provide a speaker-neutral flavour menu (absurd or everyday comparison, playful exaggeration, self-deprecating aside about the hosts or the AI field, deadpan one-liner) and SHALL instruct varying the flavour across the episode. The rule SHALL establish the overall vibe as relaxed and playful (colleagues who enjoy the subject, not news anchors).

#### Scenario: Humor block is the first engagement bullet
- **WHEN** any of the three composers builds its prompt
- **THEN** the `HUMOR & TONE` rule appears as the first bullet under "Engagement techniques", before the history-check and deep-dive instructions

#### Scenario: Humor block is not attached to the sign-off
- **WHEN** any of the three composers builds its prompt
- **THEN** the SIGN-OFF bullet contains only sign-off guidance and no humor or tone instructions

#### Scenario: Countable humor requirement
- **WHEN** the humor block is rendered
- **THEN** it requires 2-3 genuine humor moments per episode, each tied to a specific story, framed as a hard requirement

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

