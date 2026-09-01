# Capability: Voice Discovery

## Purpose

Lists the voices available to a user for a TTS provider, proxied from the provider's own API with the user's configured credentials, so voices can be picked in the dashboard.

## Requirements

### Requirement: Voice listing endpoint
The system SHALL provide a `GET /users/{userId}/voices` endpoint that returns available voices for a TTS provider. The endpoint SHALL accept a required `provider` query parameter. When `provider` is `"elevenlabs"`, it SHALL proxy the ElevenLabs `GET /v1/voices` API using the user's configured ElevenLabs API key. When `provider` is `"inworld"`, it SHALL proxy the Inworld voices API using the user's configured Inworld credentials. The response SHALL be a JSON array of voice objects with fields: `voiceId` (String), `name` (String), `category` (String, e.g., "premade", "cloned"), and `previewUrl` (String, nullable).

#### Scenario: List ElevenLabs voices
- **WHEN** a `GET /users/{userId}/voices?provider=elevenlabs` request is received and the user has an ElevenLabs TTS provider configured
- **THEN** the system returns HTTP 200 with a JSON array of voices from the ElevenLabs API

#### Scenario: List Inworld voices
- **WHEN** a `GET /users/{userId}/voices?provider=inworld` request is received and the user has Inworld credentials configured
- **THEN** the system returns HTTP 200 with a JSON array of voices from the Inworld API

#### Scenario: No provider config for ElevenLabs
- **WHEN** a `GET /users/{userId}/voices?provider=elevenlabs` request is received but the user has no ElevenLabs TTS provider configured
- **THEN** the system returns HTTP 400 with a message indicating the user must configure an ElevenLabs API key first

#### Scenario: No provider config for Inworld
- **WHEN** a `GET /users/{userId}/voices?provider=inworld` request is received but the user has no Inworld credentials configured
- **THEN** the system returns HTTP 400 with a message indicating the user must configure Inworld credentials first

#### Scenario: Unsupported provider
- **WHEN** a `GET /users/{userId}/voices?provider=openai` request is received
- **THEN** the system returns HTTP 400 indicating voice discovery is not supported for this provider

#### Scenario: Non-existing user
- **WHEN** a `GET /users/{userId}/voices?provider=inworld` request is received for a user that does not exist
- **THEN** the system returns HTTP 404

#### Scenario: Inworld API error
- **WHEN** the Inworld voices API returns an error
- **THEN** the system returns HTTP 502 with a message indicating the upstream service failed
