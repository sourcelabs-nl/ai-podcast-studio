# Spec Delta

## ADDED Requirements

### Requirement: A retry keeps audio that already exists
When a retry resumes at finalization and the episode already has an audio file on disk, the system SHALL keep that audio, mark the episode GENERATED and continue with the remaining finalization steps, and SHALL NOT synthesize the audio again.

#### Scenario: Retry after a failure that came after the audio
- **WHEN** an episode failed in a finalization step after its audio was generated, and the episode is retried
- **THEN** no text-to-speech request is made, the existing audio file is kept and the retry completes the remaining steps

### Requirement: Notifications never fail a completed episode
A failure of a static feed export or of an event listener that runs after an episode's audio was generated SHALL be logged and SHALL NOT change the episode's status to FAILED.

#### Scenario: Feed export fails after audio generation
- **WHEN** the static feed export throws after the audio of an episode was saved
- **THEN** the episode stays GENERATED and the failure is logged

#### Scenario: Listener fails on the generated event
- **WHEN** a listener of the `episode.generated` event throws
- **THEN** the episode stays GENERATED and the failure is logged
