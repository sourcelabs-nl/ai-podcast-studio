# Spec Delta

## ADDED Requirements

### Requirement: A replacement keeps its new track id when the permalink claim fails
When replacing the track of a published episode, after the replacement was uploaded and the old track deleted, the system SHALL return the new track's id and URL even if claiming the canonical permalink fails, so the publication record never keeps naming a deleted track.

#### Scenario: Permalink claim fails after the old track was deleted
- **WHEN** an episode's track is replaced, the old track is deleted, and the call that claims the canonical permalink fails
- **THEN** the replacement result carries the new track id and the URL of the uploaded track, and the failure is logged
