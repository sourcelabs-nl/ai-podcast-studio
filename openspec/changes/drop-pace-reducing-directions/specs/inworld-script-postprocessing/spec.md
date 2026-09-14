## MODIFIED Requirements

### Requirement: Delivery directions that flatten or distort a read are dropped
`InworldScriptPostProcessor` SHALL strip a steering instruction whose content names a delivery that removes expression, reduces audibility, or slows the read, instead of forwarding it to the engine.

The flattening and distorting words covered SHALL include `deadpan`, `monotone`, `monotonous`, `flat`, `flatly`, `robotic`, `mechanical`, `emotionless`, `expressionless`, `lifeless`, `dull`, `bored`, `disinterested`, `uninterested`, `whisper`, `whispering`, `whispered`, `muttering`, `mumbling`, `shouting`, `shouted`, `screaming` and `yelling`.

The pace-reducing words covered SHALL include `measured`, `slow`, `slowly`, `slower`, `deliberate`, `deliberately`, `unhurried`, `leisurely`, `languid`, `ponderous`, `plodding`, `sluggish`, `halting` and `drawling`. A pace instruction is obeyed across the whole turn: `[measured and clear]` on a 601-character expert turn in the middle of episode 208 was synthesised four times at 45.6 to 47.9 seconds against 35.6 to 36.7 seconds for the same text without it, a 27% stretch with no overlap between the two sets of samples. Preceding audio in the same voice does not soften it, so the drop SHALL apply wherever the cue appears and not only at a speaker's entrance.

A direction that adds warmth, energy or brightness SHALL be forwarded unchanged.

The match SHALL be on whole words within the instruction, so a phrase such as `[in a deadpan tone]` or `[measured and clear]` is caught and not only the bare word. Each drop SHALL be logged so the suppression is visible.

Stripping is the safe direction: the turn falls back to neutral delivery, which is never wrong, whereas a forwarded instruction is obeyed literally. `[deadpan]` on an expert turn a quarter of the way into episode 194 produced roughly 25 seconds of flat, expressionless delivery that a listener noticed. A false positive costs a little colour; a false negative costs part of an episode.

#### Scenario: A bare flattening cue is dropped
- **WHEN** a turn opens with `[deadpan]` and `retainSteeringInstructions` is true
- **THEN** the tag is removed and the turn's words are left unchanged

#### Scenario: A flattening word inside a phrase is dropped
- **WHEN** a turn opens with `[in a deadpan tone]`
- **THEN** the whole tag is removed

#### Scenario: A pace-reducing cue is dropped
- **WHEN** a turn opens with `[measured and clear]` and `retainSteeringInstructions` is true
- **THEN** the tag is removed and the turn's words are left unchanged

#### Scenario: A cue that adds energy is forwarded
- **WHEN** a turn opens with `[bright and quick]`
- **THEN** the tag is kept, because it colours the read rather than flattening or slowing it

#### Scenario: An expressive cue is still forwarded
- **WHEN** a turn opens with `[warm and conversational]`
- **THEN** the tag is kept, because it colours the read rather than flattening or slowing it

#### Scenario: Reset is still forwarded
- **WHEN** a turn contains `[reset]`
- **THEN** the tag is kept, since returning to neutral delivery is neither flattening nor slowing

#### Scenario: Sound tags are unaffected
- **WHEN** a turn contains `[sigh]` or `[laugh]`
- **THEN** the tag is kept as a sound, regardless of the suppression rule

#### Scenario: A dropped cue cannot be re-emitted onto later chunks
- **WHEN** a suppressed instruction is stripped and the turn is then split into several chunks
- **THEN** no chunk carries the instruction, because suppression happens before chunking and steering re-emission
