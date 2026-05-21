## MODIFIED Requirements

### Requirement: Continuity context in composition prompts
When previous episode recaps are available, `BriefingComposer`, `DialogueComposer`, and `InterviewComposer` SHALL accept a `previousEpisodeRecaps: List<String>` parameter (ordered most-recent-first) instead of a single nullable `String?`. When the list is non-empty, the prompt SHALL contain a "Recent episode context" section listing each recap numbered by recency. The prompt SHALL instruct the LLM:
- Do NOT repeat topics already covered in any recent episode unless there is a genuinely new development.
- If a topic was covered in a recent episode and there is new information, reference it briefly (e.g., "following up on what we covered recently...") rather than presenting it as new.
- If a topic was covered and there is no new development, skip it entirely.
- For the most recent episode (#1), the existing narrative flow instructions still apply (e.g., "as we discussed last time...").

When the list is empty, the prompt SHALL NOT include any previous episode section or continuity instructions.

#### Scenario: Composer receives multiple recaps with overlapping topic
- **WHEN** recap #1 mentions "Claude Code 1M context" and recap #3 also mentions it, and today's articles include the same Claude Code 1M story with no new developments
- **THEN** the composed script does NOT present the Claude Code 1M story as news and either skips it entirely or references it in one sentence

#### Scenario: Composer receives multiple recaps with genuinely new development
- **WHEN** recap #2 mentions "EU AI Act proposal" and today's articles include a new vote result on the EU AI Act
- **THEN** the composed script covers the vote result as new news and references the previous coverage (e.g., "following up on the EU AI Act we discussed earlier this week...")

#### Scenario: Composer receives empty recap list
- **WHEN** the composer is called with an empty list of recaps
- **THEN** the composition prompt does not include any previous episode section and the script has no back-references

#### Scenario: Composer receives single recap (backwards compatible behavior)
- **WHEN** the composer receives a list with one recap
- **THEN** the behavior matches the previous single-recap behavior with narrative flow instructions

#### Scenario: Continuity in dialogue style with multiple recaps
- **WHEN** the `DialogueComposer` receives a list of 5 recaps
- **THEN** the speakers naturally avoid repeating topics from recent episodes and reference the most recent episode in conversation

#### Scenario: Continuity in interview style with multiple recaps
- **WHEN** the `InterviewComposer` receives a list of 3 recaps
- **THEN** the interviewer and expert avoid rehashing topics from recent episodes

## MODIFIED Requirements

### Requirement: Recap stored on episode
The system SHALL store the generated recap as a nullable `recap` column on the `episodes` table. The recap SHALL be generated in `BriefingGenerationScheduler` after saving a new episode and persisted via an update to the episode. If recap generation fails, the episode SHALL remain valid with a null recap. The recap generation token usage SHALL be added to the episode's `llm_input_tokens` and `llm_output_tokens`.

The `LlmPipeline` SHALL fetch the N most recent episodes (with non-null recaps) for the podcast, where N is determined by the podcast's `recapLookbackEpisodes` field (falling back to the global `app.episode.recap-lookback-episodes` default). The recaps from these episodes SHALL be passed to the composer as a `List<String>` ordered most-recent-first.

#### Scenario: Recap persisted after episode creation
- **WHEN** a new episode is created and saved
- **THEN** the `EpisodeRecapGenerator` generates a recap from the episode's script and the episode is updated with the recap text

#### Scenario: Recap generation failure does not block episode
- **WHEN** the recap LLM call fails with an exception
- **THEN** the episode remains saved with a null `recap` and the failure is logged

#### Scenario: Old episodes without recap
- **WHEN** the pipeline fetches recent episodes and some were created before the recap feature
- **THEN** episodes with null `recap` are excluded from the list and the remaining recaps are passed to the composer

#### Scenario: Pipeline fetches configurable number of recaps
- **WHEN** a podcast has `recapLookbackEpisodes` set to 5
- **THEN** the pipeline fetches the 5 most recent episodes with non-null recaps for that podcast

#### Scenario: Pipeline uses global default when podcast has no override
- **WHEN** a podcast has `recapLookbackEpisodes` set to null and the global default is 7
- **THEN** the pipeline fetches the 7 most recent episodes with non-null recaps
