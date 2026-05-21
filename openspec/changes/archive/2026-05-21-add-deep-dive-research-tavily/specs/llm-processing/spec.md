## ADDED Requirements

### Requirement: Compose stage conditionally registers webSearch tool

`ChatClientFactory` SHALL register the `webSearch` tool for the compose stage when `podcast.deepDiveEnabled=true`, and SHALL NOT register it otherwise. Filter and score stages MUST remain tool-less.

#### Scenario: Enabled podcast registers webSearch

- **WHEN** the compose stage builds a `ChatClient` for a podcast with `deepDiveEnabled=true`
- **THEN** the client has `webSearch` registered as a callable tool

#### Scenario: Disabled podcast omits webSearch

- **WHEN** the compose stage builds a `ChatClient` for a podcast with `deepDiveEnabled=false`
- **THEN** the client has no `webSearch` tool registered

#### Scenario: Filter stage gets no tools

- **WHEN** the filter or score stage builds a `ChatClient`
- **THEN** the client has zero tools registered
