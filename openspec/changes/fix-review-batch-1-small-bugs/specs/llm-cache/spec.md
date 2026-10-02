# Spec Delta

## ADDED Requirements

### Requirement: A correction retry replaces the rejected cache entry
When a composed script is rejected by validation and the request is re-issued with the rejected response and a correction appended, `CachingChatModel` SHALL store the response to that retry under the key of the original prompt, replacing the rejected response, and SHALL NOT store it under a key derived from the correction text.

#### Scenario: Corrected response replaces the rejected one
- **WHEN** a compose response is cached, then rejected by validation, and the retry returns a valid script
- **THEN** the cache entry for the original prompt holds the retry's response, so the next identical request replays the valid script

#### Scenario: No entry is created for the correction text
- **WHEN** a retry prompt containing a correction is answered
- **THEN** no cache entry exists whose key is derived from the correction text
