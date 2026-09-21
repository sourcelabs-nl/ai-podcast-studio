# Capability: Interview Composition

## Purpose

Interview-style dialogue composition for podcast scripts, producing natural asymmetric conversations between an interviewer and expert with XML speaker tags for TTS processing.

## Requirements

### Requirement: InterviewComposer generates asymmetric speaker-tagged scripts
The system SHALL provide an `InterviewComposer` component that generates interview-style dialogue scripts with two fixed roles: `interviewer` and `expert`. The interviewer SHALL act as an active conversational partner — asking questions, bridging topics, reacting, challenging, and providing commentary (~35% of total words). The expert SHALL deliver the news content, context, and analysis (~65% of total words). The output SHALL use XML-style speaker tags `<interviewer>` and `<expert>`. The composer SHALL use the `compose` model (resolved via `ModelResolver`).

#### Scenario: Interview script generated with two speakers
- **WHEN** the `InterviewComposer` composes a script for a podcast with `ttsVoices: {"interviewer": "id1", "expert": "id2"}`
- **THEN** the output contains alternating `<interviewer>` and `<expert>` tags with the interviewer asking questions and the expert delivering content

#### Scenario: Interviewer has significant airtime
- **WHEN** the `InterviewComposer` generates a script
- **THEN** interviewer turns comprise approximately 35% of total words, including questions, reactions, challenges, and commentary, while expert turns comprise approximately 65% with substantive news content and analysis

#### Scenario: Composer uses compose model
- **WHEN** the `InterviewComposer` is invoked
- **THEN** it resolves and uses the `compose` stage model via `ModelResolver`

#### Scenario: Tags are not stripped from output
- **WHEN** the LLM produces an interview script with `<interviewer>` and `<expert>` tags
- **THEN** the tags are preserved in the returned script

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

### Requirement: Interview style routing in pipeline
The LLM pipeline SHALL route `style: "interview"` to the `InterviewComposer`. The selection SHALL happen in the pipeline orchestration layer (`LlmPipeline`).

#### Scenario: Interview style uses InterviewComposer
- **WHEN** a podcast has `style: "interview"`
- **THEN** the pipeline uses `InterviewComposer` for script generation

#### Scenario: Dialogue style still uses DialogueComposer
- **WHEN** a podcast has `style: "dialogue"`
- **THEN** the pipeline uses `DialogueComposer` for script generation

#### Scenario: Monologue styles still use BriefingComposer
- **WHEN** a podcast has `style: "news-briefing"`
- **THEN** the pipeline uses `BriefingComposer` for script generation

### Requirement: Interview prompts use variety rotation

The interview composer prompt SHALL be parameterized by the `PromptVarietyPicker` selection for the current `(podcastId, episodeDate)`. Opening style, transition vocabulary, sign-off shape, and any "coming up" teaser shape MUST come from the picker, not be hard-coded constants or verbatim example strings.

#### Scenario: Different dates produce different prompt scaffolding

- **WHEN** the interview composer builds prompts for the same podcast on two different dates with the same article set
- **THEN** the prompt strings differ in the opening-style, transition-vocabulary, and sign-off-shape sections

### Requirement: Interview prompts contain no verbatim example phrases

The interview composer prompt SHALL NOT include literal sample sentences such as `"But here's where it gets really interesting..."`, `"Coming up: AI agents going rogue..."`, `"Wait, wait — did you say 100x?!"`, or `"Stephan, thanks as always"`. The interruption-style menu SHALL describe categories (excited, skeptical, confused, connecting dots, playful disagreement) without prescribing the exact wording.

#### Scenario: No banned phrases present

- **WHEN** the interview composer prompt is built
- **THEN** the prompt does not contain any of the documented banned example phrases verbatim

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

### Requirement: The interview prompt asks for backchannels
The interview prompt SHALL ask for 2 to 3 backchannels per episode: a turn of a few words from the interviewer while the expert is mid-explanation, carrying no question, no new information and no topic change, after which the expert resumes their thought in a turn of their own.

The prompt SHALL distinguish a backchannel from an interruption, on the grounds that an interruption takes the floor and a backchannel hands it straight back.

#### Scenario: The rule reaches the model
- **WHEN** the interview prompt is built
- **THEN** it asks for backchannels and states that they carry no question and no new information

#### Scenario: The turn length cap is unchanged
- **WHEN** the interview prompt is built
- **THEN** the expert's per-turn sentence cap is stated as before

### Requirement: Alternation admits the backchannel and nothing else
The interview prompt SHALL require speaker tags to alternate, with the backchannel as the single stated exception: after a backchannel turn the speaker who was interrupted MAY resume in a turn of their own.

The prompt SHALL continue to forbid two consecutive turns by one speaker that continue the same point or that evade the per-turn length cap.

The prompt SHALL NOT claim that consecutive same-speaker turns break the TTS pipeline, since they do not: each turn's role is read from its own opening tag and a voice is resolved per turn.

#### Scenario: The exception is named and bounded
- **WHEN** the interview prompt is built
- **THEN** it names the backchannel as the one exception and still forbids a same-speaker pair used to continue a point

#### Scenario: Consecutive same-speaker turns are synthesised
- **WHEN** a script contains an expert turn, a backchannel, and a further expert turn
- **THEN** each turn is voiced by the role's own voice and the audio is produced without error
