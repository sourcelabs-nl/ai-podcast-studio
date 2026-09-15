## MODIFIED Requirements

### Requirement: InterviewComposer prompt engineering
The InterviewComposer prompt SHALL instruct the model on script structure and engagement, including the hook opening and conversational coherence.

**Hook opening:** The prompt SHALL instruct the interviewer to NOT start with a standard welcome. Instead, the interviewer SHALL open with a provocative statement, surprising fact, or compelling question drawn from the most interesting article of the day, then transition into the regular introduction.

**No empty setup turns:** The prompt SHALL forbid contentless setup turns. When a speaker announces or teases a specific point (a caveat, skeptical flag, question, fact, or statistic), that same speaker SHALL state its substance in the same turn. A handoff to the other speaker is permitted only when that speaker adds genuinely new information, not when they complete a point the first speaker merely gestured at.

**No echo turns:** The prompt SHALL forbid a turn that consists only of words echoed back from the line it follows. Short reaction turns are permitted, but outside the BACKCHANNELS device every turn SHALL be at least one complete sentence carrying its own content or its own energy, never a bare fragment lifted from the previous speaker.

#### Scenario: Hook opening instead of standard welcome
- **WHEN** the interview prompt is built
- **THEN** it instructs the interviewer to open with a hook rather than a standard welcome

#### Scenario: No contentless setup turns
- **WHEN** the interview prompt is built
- **THEN** it instructs the model that a speaker who teases a specific point must state that point in the same turn rather than handing its substance to the other speaker

#### Scenario: No echo turns
- **WHEN** the interview prompt is built
- **THEN** it instructs the model that a turn must not repeat back the previous speaker's words, and that a short reaction turn must be a complete sentence with its own content or energy
