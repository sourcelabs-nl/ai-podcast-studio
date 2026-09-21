## ADDED Requirements

### Requirement: Cliffhangers defer their payoff
The interview prompt SHALL ask for 1-2 forward hooks per episode and no more. It SHALL define a forward hook as naming something specific from a story that is NOT about to be covered, explicitly parking it, and moving on to a different topic. The prompt SHALL require at least 3 other topics to be covered before the hook is paid off, and SHALL require the payoff to open by referring back to the promise.

The prompt SHALL state that a tease the very next turn resolves is not a cliffhanger but a topic announcement, and SHALL name that failure shape so it is excluded rather than merely undescribed. Without this, the instruction to tease "before transitioning" is satisfied by placing the tease immediately ahead of the story it announces, which is how episode 208 produced zero genuine hooks while appearing to follow the rule.

Hooks SHALL be reserved for the episode's biggest stories, and the two SHALL be phrased differently.

#### Scenario: Deferral distance is required
- **WHEN** the interview prompt is built
- **THEN** it requires at least 3 other topics to be covered between a forward hook and its payoff

#### Scenario: An immediate tease is excluded by name
- **WHEN** the interview prompt is built
- **THEN** it states that a tease resolved by the very next turn is a topic announcement and does not count

#### Scenario: Fewer hooks are requested
- **WHEN** the interview prompt is built
- **THEN** it asks for 1-2 forward hooks per episode rather than 2-3

### Requirement: The introduction teaser names several topics
When the episode has enough articles for a "coming up" teaser, the prompt SHALL require the interviewer to name at least 3 distinct topics drawn from different parts of the episode, and SHALL exclude three angles on the opening story, which the listener has just heard. The teaser budget SHALL be 40 words, since 3 topics do not fit in the 25 words the rule previously allowed.

#### Scenario: Several topics required
- **WHEN** the interview prompt is built for an episode with at least 5 articles
- **THEN** the teaser rule requires at least 3 distinct topics from different parts of the episode

#### Scenario: Opening story angles excluded
- **WHEN** the interview prompt is built for an episode with at least 5 articles
- **THEN** the teaser rule excludes three angles on the opening story

#### Scenario: Budget fits the requirement
- **WHEN** the interview prompt is built for an episode with at least 5 articles
- **THEN** the teaser budget is 40 words
