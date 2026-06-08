## MODIFIED Requirements

### Requirement: DialogueComposer prompt engineering
The DialogueComposer prompt SHALL instruct the model on script structure and engagement, including conversational coherence.

**No empty setup turns:** The prompt SHALL forbid contentless setup turns. When a speaker announces or teases a specific point (a caveat, flag, question, fact, or statistic), that same speaker SHALL state its substance in the same turn. A handoff to the other speaker is permitted only when that speaker adds genuinely new information, not when they complete a point the first speaker merely gestured at.

#### Scenario: No contentless setup turns
- **WHEN** the dialogue prompt is built
- **THEN** it instructs the model that a speaker who teases a specific point must state that point in the same turn rather than handing its substance to the other speaker
