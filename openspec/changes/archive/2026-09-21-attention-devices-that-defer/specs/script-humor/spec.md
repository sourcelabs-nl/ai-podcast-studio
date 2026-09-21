## MODIFIED Requirements

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
