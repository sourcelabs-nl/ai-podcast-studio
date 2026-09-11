## MODIFIED Requirements

### Requirement: Inworld sound tag recognition
The post-processor SHALL recognise the sound names Inworld documents — `sigh`, `laugh`, `breathe`, `cough`, `clear throat`, `yawn` — case-insensitively, and SHALL rewrite an underscore in a name to a space so the legacy `[clear_throat]` spelling reaches Inworld as `[clear throat]`. A recognised sound SHALL be emitted in its documented spelling and SHALL be kept on every model, because a sound is not a steering instruction.

The post-processor SHALL additionally fold near-misses of those names onto the documented spelling: the plural and participle forms of each name (`laughs`, `laughing`, `sighs`, `sighing`, `breathes`, `breathing`, `breath`, `coughs`, `coughing`, `yawns`, `yawning`), `laughter`, the `chuckle` family (`chuckle`, `chuckles`, `chuckling`, all onto `laugh`), and the `clears throat` and `throat clear` phrasings. Without the fold an unrecognised name becomes a steering instruction, so `[laughs]` does not cost a laugh: it asks for the remainder of the turn to be read laughing, and the provider re-emits that direction onto every following chunk of the turn.

The fold SHALL cover only near-misses of the documented names. A free-form delivery direction SHALL remain a steering instruction and keep its existing handling.

#### Scenario: Documented sound name preserved
- **WHEN** `process("[laugh] Right, because why not.")` is called
- **THEN** the tag is preserved as `[laugh]`

#### Scenario: Plural sound name folded onto the documented spelling
- **WHEN** `process("[laughs] Right, because why not.", retainSteeringInstructions = true)` is called
- **THEN** the result is `[laugh] Right, because why not.`

#### Scenario: Chuckle folded onto laugh
- **WHEN** `normalizeSoundName("chuckles")` is called
- **THEN** it returns `laugh`

#### Scenario: A folded name is a sound, not an instruction
- **WHEN** `isSteeringInstruction("laughs")` is called
- **THEN** it returns false, and `process("[laughs] Right.")` keeps the tag even with steering instructions disabled

#### Scenario: A delivery direction is not folded
- **WHEN** `normalizeSoundName("warm and conversational")` or `normalizeSoundName("light")` is called
- **THEN** it returns null, and the tag is handled as a steering instruction
