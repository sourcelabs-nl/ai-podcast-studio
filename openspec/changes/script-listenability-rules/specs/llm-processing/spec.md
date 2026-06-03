## MODIFIED Requirements

### Requirement: Spoken model and product names in compose prompt
The compose-stage prompt for all three composers (`BriefingComposer`, `DialogueComposer`, `InterviewComposer`) SHALL include a shared rule instructing the LLM to write AI model, product, software package, repository, and domain names the way they are spoken aloud rather than as written. The rule SHALL direct the LLM to replace hyphens, slashes, and dots with natural spoken words or pauses, speak version numbers and standalone digits as words, and expand letter-clusters to their spoken sound, and SHALL provide concrete examples (e.g. "MAI-Code-1-Flash" spoken as "May Code One Flash", "GPT-4o" spoken as "GPT four oh", the package "datasette-agent-micropython" spoken as "the Datasette agent for MicroPython", "warp.dev" spoken as "Warp"). The rule SHALL be defined once in `ComposerUtils.kt` and shared verbatim across the three composers, alongside the existing punctuation and numbers rules.

#### Scenario: Briefing prompt includes spoken model and package names rule
- **WHEN** `BriefingComposer.compose()` builds its prompt
- **THEN** the prompt includes the spoken model, product, and package names rule with at least one worked example

#### Scenario: Dialogue prompt includes spoken model and package names rule
- **WHEN** `DialogueComposer.compose()` builds its prompt
- **THEN** the prompt includes the spoken model, product, and package names rule

#### Scenario: Interview prompt includes spoken model and package names rule
- **WHEN** `InterviewComposer.compose()` builds its prompt
- **THEN** the prompt includes the spoken model, product, and package names rule

#### Scenario: Rule is shared from a single source
- **WHEN** the spoken model, product, and package names rule text is needed by any composer
- **THEN** it is produced by a single shared function in `ComposerUtils.kt` rather than duplicated per composer

## ADDED Requirements

### Requirement: Source names not handles in compose prompt
The compose-stage prompt for all three composers SHALL include a shared rule instructing the LLM never to read social-media usernames or handles aloud (e.g. an X or GitHub handle). The rule SHALL direct the LLM to use the real person or organization name when known, otherwise a generic descriptor (e.g. "a developer on X"), and SHALL forbid wrapping handles in slashes or any phoneme notation (the IPA slash notation is reserved for listed pronunciation-guide terms). The rule SHALL be defined once in `ComposerUtils.kt` and shared verbatim across the three composers.

#### Scenario: Briefing prompt includes source-names-not-handles rule
- **WHEN** `BriefingComposer.compose()` builds its prompt
- **THEN** the prompt instructs the LLM not to read raw social-media handles aloud

#### Scenario: Dialogue prompt includes source-names-not-handles rule
- **WHEN** `DialogueComposer.compose()` builds its prompt
- **THEN** the prompt instructs the LLM not to read raw social-media handles aloud

#### Scenario: Interview prompt includes source-names-not-handles rule
- **WHEN** `InterviewComposer.compose()` builds its prompt
- **THEN** the prompt instructs the LLM not to read raw social-media handles aloud

### Requirement: Research names for the ear in compose prompt
The compose-stage prompt for all three composers SHALL include a shared rule instructing the LLM to ration unfamiliar research proper-names. The rule SHALL direct the LLM to lead with what a piece of research does, voice a paper codename only when the name itself is the news, soften or drop author-surname attributions (at most crediting a notable lab or company), and avoid reciting more than one unfamiliar proper name per sentence. The rule SHALL be defined once in `ComposerUtils.kt` and shared verbatim across the three composers.

#### Scenario: Briefing prompt includes research-names-for-the-ear rule
- **WHEN** `BriefingComposer.compose()` builds its prompt
- **THEN** the prompt instructs the LLM to lead with what research does and ration unfamiliar proper names

#### Scenario: Dialogue prompt includes research-names-for-the-ear rule
- **WHEN** `DialogueComposer.compose()` builds its prompt
- **THEN** the prompt instructs the LLM to lead with what research does and ration unfamiliar proper names

#### Scenario: Interview prompt includes research-names-for-the-ear rule
- **WHEN** `InterviewComposer.compose()` builds its prompt
- **THEN** the prompt instructs the LLM to lead with what research does and ration unfamiliar proper names
