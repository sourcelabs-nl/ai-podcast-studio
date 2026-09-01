## ADDED Requirements

### Requirement: Delivery directions may colour a read, never flatten it
The Inworld script guidelines SHALL tell the composer that a delivery direction may adjust warmth, energy or pace, and SHALL NOT ask for a delivery that removes expression or makes a turn harder to hear — naming `deadpan`, `monotone`, `robotic` and `whispering` as examples to avoid.

The engine obeys such a direction literally, so the instruction is the first line of defence and the post-processor's suppression rule is the backstop. `[deadpan]` reached episode 194 this way and flattened an expert turn for roughly 25 seconds.

#### Scenario: Guidelines constrain delivery directions
- **WHEN** the Inworld guidelines are built for any podcast style
- **THEN** they state that a delivery direction may adjust warmth, energy or pace but must not remove expression or reduce audibility

#### Scenario: Flattening examples are named
- **WHEN** the guidelines describe delivery directions
- **THEN** they name at least `deadpan` and `monotone` as directions not to use
