## ADDED Requirements

### Requirement: Delivery directions that flatten or distort a read are dropped
`InworldScriptPostProcessor` SHALL strip a steering instruction whose content names a delivery that removes expression or reduces audibility, instead of forwarding it to the engine. The words covered SHALL include `deadpan`, `monotone`, `monotonous`, `flat`, `flatly`, `robotic`, `mechanical`, `emotionless`, `expressionless`, `lifeless`, `dull`, `bored`, `disinterested`, `uninterested`, `whisper`, `whispering`, `whispered`, `muttering`, `mumbling`, `shouting`, `shouted`, `screaming` and `yelling`.

The match SHALL be on whole words within the instruction, so a phrase such as `[in a deadpan tone]` or `[flat and bored]` is caught and not only the bare word. Each drop SHALL be logged so the suppression is visible.

Stripping is the safe direction: the turn falls back to neutral delivery, which is never wrong, whereas a forwarded instruction is obeyed literally. `[deadpan]` on an expert turn a quarter of the way into episode 194 produced roughly 25 seconds of flat, expressionless delivery that a listener noticed. A false positive costs a little colour; a false negative costs part of an episode.

This rule SHALL apply only where instructions would otherwise be retained — for a model without steering support every instruction is stripped already.

#### Scenario: A bare flattening cue is dropped
- **WHEN** a turn opens with `[deadpan]` and `retainSteeringInstructions` is true
- **THEN** the tag is removed and the turn's words are left unchanged

#### Scenario: A flattening word inside a phrase is dropped
- **WHEN** a turn opens with `[in a deadpan tone]`
- **THEN** the whole tag is removed

#### Scenario: An expressive cue is still forwarded
- **WHEN** a turn opens with `[warm and conversational with an easy pace]`
- **THEN** the tag is kept, because it colours the read rather than flattening it

#### Scenario: Reset is still forwarded
- **WHEN** a turn contains `[reset]`
- **THEN** the tag is kept, since returning to neutral delivery is not a flattening instruction

#### Scenario: Sound tags are unaffected
- **WHEN** a turn contains `[sigh]` or `[laugh]`
- **THEN** the tag is kept as a sound, regardless of the suppression rule

#### Scenario: A dropped cue cannot be re-emitted onto later chunks
- **WHEN** a suppressed instruction is stripped and the turn is then split into several chunks
- **THEN** no chunk carries the instruction, because suppression happens before chunking and steering re-emission
