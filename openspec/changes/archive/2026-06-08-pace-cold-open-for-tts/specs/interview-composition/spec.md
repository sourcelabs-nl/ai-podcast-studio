## MODIFIED Requirements

### Requirement: InterviewComposer prompt engineering
The InterviewComposer prompt SHALL instruct the model on script structure and engagement, including the hook opening.

**Hook opening:** The prompt SHALL instruct the interviewer to NOT start with a standard welcome. Instead, the interviewer SHALL open with a provocative statement, surprising fact, or compelling question drawn from the most interesting article of the day, then transition into the regular introduction.

**Opening pacing:** The prompt SHALL instruct the model to write the hook/cold-open as a few short, punchy sentences rather than one long comma-stacked run-on, using full stops (and an occasional ellipsis) as pacing beats, so the TTS engine does not rush the opening and its spoken pace stays even with the rest of the episode.

#### Scenario: Hook opening instead of standard welcome
- **WHEN** the interview prompt is built
- **THEN** it instructs the interviewer to open with a hook (provocative statement, surprising fact, or question) rather than a standard welcome

#### Scenario: Opening is paced for TTS
- **WHEN** the interview prompt is built
- **THEN** it instructs the model to keep the opening to a few short sentences with full-stop/ellipsis pacing beats rather than one long run-on sentence
