## MODIFIED Requirements

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
