# Capability: Episode Continuity

## Purpose

Episode continuity context enables podcast scripts to reference previous episodes, creating a sense of ongoing narrative. A recap is generated from each episode's script and stored for use by subsequent episodes.
## Requirements
### Requirement: Episode recap generation
The system SHALL provide an `EpisodeRecapGenerator` component that accepts an episode's `scriptText` and produces a 2-3 sentence plain-text recap of the key topics discussed. The recap SHALL be generated using the filter model (resolved via `ModelResolver` for the `filter` stage). The recap prompt SHALL instruct the LLM to summarize the main topics covered in the episode script concisely, without any meta-commentary or preamble. Token usage from the recap call SHALL be tracked.

#### Scenario: Recap generated from episode script
- **WHEN** `EpisodeRecapGenerator.generate()` is called with a 1500-word episode script
- **THEN** the result is a 2-3 sentence plain-text summary of the key topics discussed

#### Scenario: Recap uses filter model
- **WHEN** the recap is generated
- **THEN** the system uses the model resolved for the `filter` stage via `ModelResolver`

#### Scenario: Token usage tracked
- **WHEN** the recap LLM call uses 800 input tokens and 60 output tokens
- **THEN** those tokens are returned in the `RecapResult` and added to the episode's token totals

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

### Requirement: Continuity context in composition prompts
When composing a script, the system SHALL NOT pass episode recaps to the composer. Continuity context SHALL instead be provided by the topic dedup filter's `[FOLLOW-UP: ...]` annotations on CONTINUATION articles. The `previousEpisodeRecaps` parameter SHALL be removed from all three composers (`BriefingComposer`, `DialogueComposer`, `InterviewComposer`). The `recapBlock` section SHALL be removed from all composer prompts.

The composer SHALL still naturally reference previous coverage when it sees `[FOLLOW-UP: ...]` headers above article groups, using phrasing like "following up on what we covered recently..." or "as we discussed last time..."

Recap generation SHALL continue unchanged — recaps are still generated and stored on episodes for use in publication descriptions (feed.xml, SoundCloud).

#### Scenario: Composer receives continuation articles with follow-up annotation
- **WHEN** the dedup filter identifies a CONTINUATION topic about "Gemini 2.5 pricing" (previously covered: release and benchmarks)
- **THEN** the composer prompt includes a `[FOLLOW-UP: ...]` header above the Gemini articles, and the composed script references the previous coverage naturally

#### Scenario: Composer receives new articles without annotation
- **WHEN** the dedup filter identifies a NEW topic about "NVIDIA Blackwell pricing"
- **THEN** the composer prompt includes the articles without any follow-up header, and the script presents it as fresh news

#### Scenario: No previous episodes — no annotations
- **WHEN** the dedup filter has no historical articles (first episode)
- **THEN** all articles appear as NEW clusters without annotations, and the composer produces a script with no back-references

#### Scenario: Recap still generated for publication
- **WHEN** an episode is created and its script is generated
- **THEN** the `EpisodeRecapGenerator` still generates a 2-3 sentence recap stored on the episode for feed.xml and SoundCloud descriptions

