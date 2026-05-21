# Capability: Dialogue Composition

## Purpose

Multi-speaker dialogue composition for podcast scripts, producing natural conversations with XML speaker tags for TTS processing.
## Requirements
### Requirement: DialogueComposer generates speaker-tagged scripts
The system SHALL provide a `DialogueComposer` component that generates multi-speaker dialogue scripts using XML-style speaker tags. The composer SHALL use the `compose` model (resolved via `ModelResolver`). The output format SHALL use tags matching the keys in the podcast's `ttsVoices` map (e.g., `<host>`, `<cohost>`). The composer SHALL NOT strip any tags from the output — the tags are required for downstream processing.

#### Scenario: Dialogue script generated with two speakers
- **WHEN** the `DialogueComposer` composes a script for a podcast with `ttsVoices: {"host": "id1", "cohost": "id2"}`
- **THEN** the output contains alternating `<host>` and `<cohost>` tags with natural conversation

#### Scenario: Composer uses compose model
- **WHEN** the `DialogueComposer` is invoked
- **THEN** it resolves and uses the `compose` stage model via `ModelResolver`

#### Scenario: Tags are not stripped from output
- **WHEN** the LLM produces a dialogue script with `<host>` and `<cohost>` tags
- **THEN** the tags are preserved in the returned script (no `stripSectionHeaders` applied)

### Requirement: DialogueComposer prompt engineering
The `DialogueComposer` prompt SHALL instruct the LLM to produce a natural conversation between the configured speaker roles. The prompt SHALL include: the podcast name, topic, current date, article summaries (same format as `BriefingComposer`), target word count, and language. The prompt SHALL specify that the output must use the exact XML tag names corresponding to the `ttsVoices` keys. The prompt SHALL instruct speakers to have distinct personalities — the host drives the conversation and the co-host provides reactions, analysis, and follow-up questions. The prompt SHALL prohibit any text outside of speaker tags. The prompt SHALL clarify that TTS-supported cues (as described in the TTS formatting section, when present) ARE allowed inside speaker tags.

The prompt SHALL include the following engagement techniques:

**Hook opening:** The prompt SHALL instruct the host to NOT start with a standard welcome. Instead, the host SHALL open with a provocative statement, surprising fact, or compelling question drawn from the most interesting article of the day, then transition into the regular introduction.

**Front-load best story:** The prompt SHALL instruct the LLM to lead with the most compelling or surprising article, not the order they appear in the summaries.

**Curiosity hooks:** The prompt SHALL instruct speakers to use rhetorical questions and teaser hooks before transitions (e.g., "But here's where it gets really interesting...", "So why should we care?"). These create micro-curiosity loops that pull listeners forward.

**Mid-roll callbacks:** The prompt SHALL instruct speakers to reference earlier topics later in the episode to create narrative cohesion (e.g., "Remember that thing we talked about earlier? Well, this connects directly..."). The prompt SHALL require at least one callback per episode.

**Short segments with signposting:** The prompt SHALL instruct speakers to keep individual topic segments concise (roughly 60-90 seconds each). The prompt SHALL instruct the host to use clear verbal signposts so listeners always know where they are.

**Natural interruptions:** The prompt SHALL instruct speakers to occasionally interrupt each other mid-topic — not at the end of a complete explanation, but while the other speaker is still building their point. Each speaker turn SHALL be kept to 3-5 sentences max. The other speaker jumps in with a reaction, follow-up question, or interjection. The original speaker then continues in their next turn. The prompt SHALL target 3-4 interruptions per episode, spread across different topics.

**Emphasis on important announcements:** The prompt SHALL instruct speakers to convey significance when covering major announcements or surprising developments — using emphatic language, exclamation marks, and brief pauses to let important news land. Not everything is exciting; the prompt SHALL instruct speakers to save the energy for what truly stands out.

When TTS script guidelines are provided, the prompt SHALL include them as an additional instruction block. When no guidelines are provided, the prompt SHALL NOT include any emotion cue instructions.

When `speakerNames` is provided on the podcast, the prompt SHALL instruct the LLM to use these display names in conversation (e.g., "Hey Sarah, did you see this?") while keeping role keys as the XML tag names. When `speakerNames` is not provided, the prompt SHALL use role keys as speaker identifiers (existing behavior).

When a previous episode recap is provided, the prompt SHALL include a "Previous episode context" section containing the recap text. The prompt SHALL instruct the speakers to naturally reference the previous episode in conversation. When today's topics relate to the previous episode, the speakers SHALL weave in specific back-references. When topics are unrelated, the host SHALL include a brief mention of the previous episode in the opening.

#### Scenario: Prompt includes speaker role names
- **WHEN** the podcast has `ttsVoices: {"host": "id1", "cohost": "id2"}`
- **THEN** the prompt instructs the LLM to use `<host>` and `<cohost>` tags

#### Scenario: Prompt uses speaker display names when provided
- **WHEN** the podcast has `ttsVoices: {"host": "id1", "cohost": "id2"}` and `speakerNames: {"host": "Sarah", "cohost": "Mike"}`
- **THEN** the prompt instructs the LLM to use "Sarah" and "Mike" in conversation while keeping `<host>` and `<cohost>` as XML tags

#### Scenario: Prompt uses role keys when no names provided
- **WHEN** the podcast has `ttsVoices: {"host": "id1", "cohost": "id2"}` and no `speakerNames`
- **THEN** the prompt uses "host" and "cohost" as speaker identifiers (unchanged behavior)

#### Scenario: Prompt includes article summaries
- **WHEN** 5 articles are passed to the composer
- **THEN** the prompt includes all 5 article summaries with source attribution

#### Scenario: Prompt respects podcast language
- **WHEN** the podcast has `language: "nl"`
- **THEN** the prompt instructs the LLM to write the dialogue in Dutch

#### Scenario: Prompt includes TTS provider guidelines
- **WHEN** the composer is called with Inworld TTS script guidelines
- **THEN** the prompt includes Inworld-specific instructions for emotion tags, emphasis, and non-verbal cues

#### Scenario: Prompt without guidelines omits emotion instructions
- **WHEN** the composer is called with empty TTS script guidelines
- **THEN** the prompt does not include any emotion cue or TTS-specific instructions

#### Scenario: Custom instructions included
- **WHEN** the podcast has `customInstructions: "Focus on practical implications"`
- **THEN** the prompt includes these instructions

#### Scenario: Hook opening instead of standard welcome
- **WHEN** the `DialogueComposer` generates a script
- **THEN** the prompt instructs the host to open with a provocative statement, surprising fact, or compelling question rather than a standard welcome

#### Scenario: Best story is front-loaded
- **WHEN** the `DialogueComposer` generates a script from 5 articles
- **THEN** the prompt instructs the LLM to lead with the most compelling or surprising article regardless of input order

#### Scenario: Curiosity hooks used in transitions
- **WHEN** the `DialogueComposer` generates a script with multiple topics
- **THEN** the prompt instructs speakers to use rhetorical questions and teaser hooks before transitions

#### Scenario: Mid-roll callbacks create cohesion
- **WHEN** the `DialogueComposer` generates a script with 4+ articles
- **THEN** the prompt instructs speakers to reference earlier topics at least once later in the episode

#### Scenario: Short segments with signposting
- **WHEN** the `DialogueComposer` generates a script
- **THEN** the prompt instructs keeping topic segments to roughly 60-90 seconds each with clear verbal signposts

#### Scenario: Natural mid-sentence interruptions
- **WHEN** the `DialogueComposer` generates a script
- **THEN** the prompt instructs speakers to interrupt each other mid-topic 3-4 times per episode with brief reactions or follow-up questions

#### Scenario: Emphasis on important announcements
- **WHEN** the `DialogueComposer` generates a script covering a major announcement
- **THEN** the prompt instructs speakers to convey significance through emphatic language, exclamation marks, and pacing

#### Scenario: TTS cues allowed inside speaker tags
- **WHEN** the `DialogueComposer` generates a script with Inworld TTS guidelines
- **THEN** the prompt clarifies that TTS-supported cues (e.g., `[laugh]`, `*emphasis*`) ARE allowed inside speaker tags

#### Scenario: Dialogue includes continuity with overlapping topics
- **WHEN** the previous episode recap mentions "AI chip shortage" and today's articles include new developments on chip supply
- **THEN** the dialogue naturally references the previous episode (e.g., host says "remember last time we talked about the chip shortage?")

#### Scenario: Dialogue includes brief continuity with unrelated topics
- **WHEN** the previous episode recap mentions "cryptocurrency trends" and today's articles are about climate policy
- **THEN** the host briefly mentions the previous episode in the opening before moving to today's topics

### Requirement: Composer selection based on podcast style
The system SHALL select the appropriate composer based on the podcast's `style` field. The `"dialogue"` style SHALL use `DialogueComposer`. The `"interview"` style SHALL use `InterviewComposer`. All other styles SHALL use `BriefingComposer`. The selection SHALL happen in the pipeline orchestration layer (`LlmPipeline`).

#### Scenario: Dialogue style uses DialogueComposer
- **WHEN** a podcast has `style: "dialogue"`
- **THEN** the pipeline uses `DialogueComposer` for script generation

#### Scenario: News-briefing style uses BriefingComposer
- **WHEN** a podcast has `style: "news-briefing"`
- **THEN** the pipeline uses `BriefingComposer` for script generation

#### Scenario: Casual style uses BriefingComposer
- **WHEN** a podcast has `style: "casual"`
- **THEN** the pipeline uses `BriefingComposer` for script generation

### Requirement: Dialogue prompts use variety rotation

The dialogue composer prompt SHALL be parameterized by the `PromptVarietyPicker` selection for the current `(podcastId, episodeDate)`. Opening style, transition vocabulary, and sign-off shape MUST come from the picker, not be hard-coded constants.

#### Scenario: Different dates produce different prompt scaffolding

- **WHEN** the dialogue composer builds prompts for the same podcast on two different dates with the same article set
- **THEN** the prompt strings differ in the opening-style and sign-off-shape sections

### Requirement: Dialogue prompts contain no verbatim example phrases

The dialogue composer prompt SHALL NOT include verbatim sample sentences that the LLM is prone to copy into generated scripts. Structural beats may be described abstractly.

#### Scenario: No banned phrases present

- **WHEN** the dialogue composer prompt is built
- **THEN** the prompt does not contain any documented banned example phrase verbatim

