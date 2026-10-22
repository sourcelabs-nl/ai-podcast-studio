## ADDED Requirements

### Requirement: Days and times are stated, not assumed
Every compose-stage prompt (briefing, dialogue, interview) SHALL include a shared rule that states today's and yesterday's weekday in English, where yesterday is the calendar day before the episode date. The rule SHALL allow naming a weekday only when it is one of those two or a day the articles themselves state, and SHALL forbid using a weekday as a figure of speech. The rule SHALL forbid assuming the listener's time of day (such as "good morning", "good evening", "this morning", "tonight" or "over breakfast"), both as a greeting and for when news broke, and SHALL direct the LLM to say "today" or "yesterday" instead. First-person anecdotes SHALL remain allowed, provided they happen on a day that fits these rules. The rule SHALL be defined once in `ComposerUtils.kt` and shared verbatim across the three composers.

#### Scenario: Every composer prompt carries the days and times rule
- **WHEN** `BriefingComposer`, `DialogueComposer` or `InterviewComposer` builds its prompt for an episode dated Thursday
- **THEN** the prompt states that today is Thursday and yesterday was Wednesday and forbids time-of-day assumptions

#### Scenario: Yesterday is the calendar day before
- **WHEN** the episode date is a Monday
- **THEN** the rule names Sunday as yesterday
