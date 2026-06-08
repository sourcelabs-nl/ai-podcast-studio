## MODIFIED Requirements

### Requirement: DialogueComposer prompt engineering
The DialogueComposer prompt SHALL instruct the model on script structure and engagement, including the hook opening.

**Hook opening:** The prompt SHALL instruct the first speaker to NOT start with a standard welcome, instead opening with a hook drawn from the most interesting article, then transitioning into the regular introduction.

**Opening pacing:** The prompt SHALL instruct the model to write the hook/cold-open as a few short, punchy sentences rather than one long comma-stacked run-on, using full stops (and an occasional ellipsis) as pacing beats, so the TTS engine does not rush the opening and its spoken pace stays even with the rest of the episode.

#### Scenario: Opening is paced for TTS
- **WHEN** the dialogue prompt is built
- **THEN** it instructs the model to keep the opening to a few short sentences with full-stop/ellipsis pacing beats rather than one long run-on sentence
