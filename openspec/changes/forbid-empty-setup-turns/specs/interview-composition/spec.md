## MODIFIED Requirements

### Requirement: InterviewComposer prompt engineering
The InterviewComposer prompt SHALL instruct the model on script structure and engagement, including the hook opening and conversational coherence.

**Hook opening:** The prompt SHALL instruct the interviewer to NOT start with a standard welcome. Instead, the interviewer SHALL open with a provocative statement, surprising fact, or compelling question drawn from the most interesting article of the day, then transition into the regular introduction.

**No empty setup turns:** The prompt SHALL forbid contentless setup turns. When a speaker announces or teases a specific point (a caveat, skeptical flag, question, fact, or statistic), that same speaker SHALL state its substance in the same turn. A handoff to the other speaker is permitted only when that speaker adds genuinely new information, not when they complete a point the first speaker merely gestured at.

#### Scenario: Hook opening instead of standard welcome
- **WHEN** the interview prompt is built
- **THEN** it instructs the interviewer to open with a hook rather than a standard welcome

#### Scenario: No contentless setup turns
- **WHEN** the interview prompt is built
- **THEN** it instructs the model that a speaker who teases a specific point must state that point in the same turn rather than handing its substance to the other speaker
