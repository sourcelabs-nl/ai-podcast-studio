## MODIFIED Requirements

### Requirement: InterviewComposer prompt engineering
The `InterviewComposer` prompt SHALL instruct the LLM to produce a natural interview-style conversation. The prompt SHALL include: the podcast name, topic, current date, article summaries with source attribution, target word count, and language. The prompt SHALL define the interviewer role as asking questions, bridging between topics, reacting, challenging, and providing commentary (~35% of words). The prompt SHALL define the expert role as delivering news content, providing context, and offering analysis (~65% of words). The prompt SHALL specify that ALL text MUST be inside `<interviewer>` or `<expert>` tags. The prompt SHALL prohibit any text outside of speaker tags, stage directions, sound effects, or meta-commentary. The prompt SHALL clarify that TTS-supported cues (as described in the TTS formatting section, when present) ARE allowed inside speaker tags.

The prompt SHALL include the following engagement techniques:

**Hook opening:** The prompt SHALL instruct the interviewer to NOT start with a standard welcome. Instead, the interviewer SHALL open with a provocative statement, surprising fact, or compelling question drawn from the most interesting article of the day, then transition into the regular introduction.

**Front-load best story:** The prompt SHALL instruct the LLM to lead with the most compelling or surprising article, not the order they appear in the summaries.

**Coming up teaser:** When 5 or more articles are provided, the prompt SHALL instruct the interviewer to include a "coming up" teaser segment immediately after the sponsor message (or after the introduction if no sponsor is configured). The teaser SHALL preview 3-4 of the most interesting topics in punchy one-liner format. When fewer than 5 articles are provided, this instruction SHALL be omitted.

#### Scenario: Coming up teaser included with many articles
- **WHEN** the `InterviewComposer` composes a script with 5 or more articles and a sponsor configured
- **THEN** the prompt includes an instruction for the interviewer to preview 3-4 topics after the sponsor message

#### Scenario: Coming up teaser omitted with few articles
- **WHEN** the `InterviewComposer` composes a script with fewer than 5 articles
- **THEN** the prompt does NOT include the "coming up" teaser instruction

#### Scenario: Coming up teaser placed after introduction when no sponsor
- **WHEN** the `InterviewComposer` composes a script with 5+ articles and no sponsor configured
- **THEN** the prompt instructs the interviewer to place the teaser after the introduction

**Curiosity hooks:** The prompt SHALL instruct the interviewer to use rhetorical questions and teaser hooks before transitions (e.g., "But here's where it gets really interesting...", "So why should we care?"). These create micro-curiosity loops that pull listeners forward.

**Mid-roll callbacks:** The prompt SHALL instruct speakers to reference earlier topics later in the episode to create narrative cohesion (e.g., "Remember that framework we discussed earlier? Well, this connects directly..."). The prompt SHALL require at least one callback per episode.

**Short segments with signposting:** The prompt SHALL instruct speakers to keep individual topic segments concise (roughly 60-90 seconds each). The prompt SHALL instruct the interviewer to use clear verbal signposts so listeners always know where they are.

**Strategic cliffhangers:** The prompt SHALL instruct the interviewer to include 2-3 forward hooks spread across the episode. Before transitioning to a new topic, the interviewer SHALL tease something from a later story to keep listeners hooked (e.g., "We'll dig into that bombshell in a moment, but first...", "And this actually connects to something wild we'll get to later — I don't want to spoil it yet.", "Keep that in mind, because it's about to become very relevant."). The prompt SHALL specify not to overuse cliffhangers — only 2-3 per episode at natural transition points.

#### Scenario: Strategic cliffhangers in transitions
- **WHEN** the `InterviewComposer` generates a script with multiple topics
- **THEN** the prompt instructs the interviewer to include 2-3 forward hooks that tease upcoming stories before transitioning

**Spontaneous interruptions:** The prompt SHALL instruct the interviewer to interrupt the expert 4-5 times per episode with genuine, varied reactions. The prompt SHALL specify the following interruption types with examples:
- Excited: "Wait, wait — did you say 100x?!"
- Skeptical: "Hold on, I'm not buying that. Isn't that exactly what they said last year?"
- Confused (audience surrogate): "Okay you lost me — back up. What does that actually mean?"
- Connecting dots: "Oh! That reminds me of what we just talked about with..."
- Playful disagreement: "See, I actually think that's completely wrong, and here's why..."

The prompt SHALL instruct that interruptions feel emotional and spontaneous, not scripted. The prompt SHALL also instruct that the expert can push back (e.g., "No no, let me finish this part because it changes everything.").

#### Scenario: Varied spontaneous interruptions
- **WHEN** the `InterviewComposer` generates a script
- **THEN** the prompt instructs the interviewer to use 4-5 interruptions of varied types (excited, skeptical, confused, connecting dots, disagreement)

#### Scenario: Expert pushback on interruptions
- **WHEN** the prompt includes spontaneous interruption instructions
- **THEN** the prompt also instructs the expert to occasionally push back on interruptions

**Strict turn length:** The prompt SHALL enforce that the expert MUST NOT speak for more than 3-4 sentences in a single turn. After that, the interviewer MUST jump in — even with a short reaction. The prompt SHALL frame this as a hard rule, not a suggestion, and cite listener drop-off as the reason.

#### Scenario: Expert turn length strictly enforced
- **WHEN** the `InterviewComposer` generates a script
- **THEN** the prompt enforces a hard maximum of 3-4 sentences per expert turn

**Emphasis on important announcements:** The prompt SHALL instruct speakers to convey significance when covering major announcements or surprising developments — using emphatic language, exclamation marks, and brief pauses to let important news land. Not every story warrants peak emphasis; the prompt SHALL instruct speakers to reserve the strongest emphasis for the news that truly stands out, and SHALL clarify that this tempers emphasis only, not the playful tone established by the humor rule (see the `script-humor` capability).

The prompt SHALL include transition guidance to ensure natural speaker handovers:
- The prompt SHALL instruct speakers to NOT start turns with bare name addresses (e.g., "Jarno, the market...")
- The prompt SHALL instruct the interviewer to use conversational bridges — reactions, follow-ups, or connectors — before transitioning to a new question or topic (e.g., "That's a great point. Now I'm curious about...", "Interesting. Speaking of which...")
- The prompt SHALL instruct that when names are used, they appear mid-sentence or at the end of questions (e.g., "What do you make of this, Jarno?") rather than as bare turn openers
- The prompt SHALL instruct speakers to vary their transition patterns — not every handover needs to use the other speaker's name

When `speakerNames` is provided on the podcast, the prompt SHALL instruct the LLM to use these names in conversation (e.g., "So Bob, what happened this week?"). When `speakerNames` is not provided, the prompt SHALL instruct speakers to address each other without names.

When a previous episode recap is provided, the prompt SHALL include a "Previous episode context" section. The prompt SHALL instruct the interviewer to naturally reference the previous episode when bridging topics. When topics are unrelated, the interviewer SHALL briefly mention the previous episode in the opening.

When `customInstructions` is provided on the podcast, the prompt SHALL append them.

#### Scenario: Interview script generated with two speakers
- **WHEN** the `InterviewComposer` composes a script for a podcast with `ttsVoices: {"interviewer": "id1", "expert": "id2"}`
- **THEN** the output contains alternating `<interviewer>` and `<expert>` tags with the interviewer asking questions and the expert delivering content

#### Scenario: Prompt uses speaker display names
- **WHEN** the podcast has `speakerNames: {"interviewer": "Alice", "expert": "Bob"}`
- **THEN** the prompt instructs the LLM to use "Alice" and "Bob" in conversation while keeping `<interviewer>` and `<expert>` as the XML tags

#### Scenario: Prompt works without speaker names
- **WHEN** the podcast has no `speakerNames`
- **THEN** the prompt instructs speakers to address each other without using names

#### Scenario: Prompt includes article summaries
- **WHEN** 5 articles are passed to the composer
- **THEN** the prompt includes all 5 article summaries with source attribution

#### Scenario: Prompt respects podcast language
- **WHEN** the podcast has `language: "nl"`
- **THEN** the prompt instructs the LLM to write the interview in Dutch

#### Scenario: Custom instructions included
- **WHEN** the podcast has `customInstructions: "Focus on practical implications"`
- **THEN** the prompt includes these instructions

#### Scenario: Hook opening instead of standard welcome
- **WHEN** the `InterviewComposer` generates a script
- **THEN** the prompt instructs the interviewer to open with a provocative statement, surprising fact, or compelling question rather than a standard welcome

#### Scenario: Best story is front-loaded
- **WHEN** the `InterviewComposer` generates a script from 5 articles
- **THEN** the prompt instructs the LLM to lead with the most compelling or surprising article regardless of input order

#### Scenario: Curiosity hooks used in transitions
- **WHEN** the `InterviewComposer` generates a script with multiple topics
- **THEN** the prompt instructs the interviewer to use rhetorical questions and teaser hooks before transitions

#### Scenario: Mid-roll callbacks create cohesion
- **WHEN** the `InterviewComposer` generates a script with 4+ articles
- **THEN** the prompt instructs speakers to reference earlier topics at least once later in the episode

#### Scenario: Short segments with signposting
- **WHEN** the `InterviewComposer` generates a script
- **THEN** the prompt instructs keeping topic segments to roughly 60-90 seconds each with clear verbal signposts

#### Scenario: Emphasis on important announcements
- **WHEN** the `InterviewComposer` generates a script covering a major announcement
- **THEN** the prompt instructs speakers to convey significance through emphatic language, exclamation marks, and pacing
- **AND** the prompt clarifies that rationing emphasis does not suppress the playful tone from the humor rule

#### Scenario: TTS cues allowed inside speaker tags
- **WHEN** the `InterviewComposer` generates a script with Inworld TTS guidelines
- **THEN** the prompt clarifies that TTS-supported cues (e.g., `[laugh]`, `*emphasis*`) ARE allowed inside speaker tags

#### Scenario: Interview includes continuity with related topics
- **WHEN** the previous episode recap mentions "AI regulation" and today's articles include new EU AI Act developments
- **THEN** the interviewer naturally references the previous episode (e.g., "We talked about AI regulation last time — any updates?")

#### Scenario: Interview includes brief continuity with unrelated topics
- **WHEN** the previous episode recap mentions "cryptocurrency" and today's articles are about climate policy
- **THEN** the interviewer briefly mentions the previous episode in the opening before moving to today's topics

#### Scenario: Transitions use conversational bridges not bare name addresses
- **WHEN** the LLM generates an interview script with speaker names configured
- **THEN** speaker turns do not begin with bare name addresses (e.g., NOT "Jarno, the economy...") but instead use natural bridges with names placed mid-sentence or at the end (e.g., "What do you think, Jarno?")

#### Scenario: Transition patterns are varied
- **WHEN** the LLM generates an interview script with 10+ speaker transitions
- **THEN** the transitions use varied patterns — some with reactions, some with follow-up questions, some with topic bridges — rather than a single repeated pattern
