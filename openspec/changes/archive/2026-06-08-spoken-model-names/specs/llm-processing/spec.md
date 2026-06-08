## ADDED Requirements

### Requirement: Spoken model and product names in compose prompt
The compose-stage prompt for all three composers (`BriefingComposer`, `DialogueComposer`, `InterviewComposer`) SHALL include a shared rule instructing the LLM to write AI model and product names the way they are spoken aloud rather than as written. The rule SHALL direct the LLM to replace hyphens with spaces, speak version numbers and standalone digits as words, and expand letter-clusters to their spoken sound, and SHALL provide concrete examples (e.g. "MAI-Code-1-Flash" spoken as "May Code One Flash", "GPT-4o" spoken as "GPT four oh"). The rule SHALL be defined once in `ComposerUtils.kt` and shared verbatim across the three composers, alongside the existing punctuation and numbers rules.

#### Scenario: Briefing prompt includes spoken model names rule
- **WHEN** `BriefingComposer.compose()` builds its prompt
- **THEN** the prompt includes the spoken model and product names rule with at least one worked example

#### Scenario: Dialogue prompt includes spoken model names rule
- **WHEN** `DialogueComposer.compose()` builds its prompt
- **THEN** the prompt includes the spoken model and product names rule

#### Scenario: Interview prompt includes spoken model names rule
- **WHEN** `InterviewComposer.compose()` builds its prompt
- **THEN** the prompt includes the spoken model and product names rule

#### Scenario: Rule is shared from a single source
- **WHEN** the spoken model and product names rule text is needed by any composer
- **THEN** it is produced by a single shared function in `ComposerUtils.kt` rather than duplicated per composer
