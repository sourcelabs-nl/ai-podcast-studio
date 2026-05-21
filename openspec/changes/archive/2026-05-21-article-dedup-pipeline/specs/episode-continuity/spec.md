## MODIFIED Requirements

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
