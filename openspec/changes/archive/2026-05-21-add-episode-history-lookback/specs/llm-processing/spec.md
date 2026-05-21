## ADDED Requirements

### Requirement: Compose stage registers tools, other stages do not

`ChatClientFactory` SHALL expose a compose-specific construction path that registers `searchPastEpisodes` as a callable tool. The filter and score stages SHALL receive a tool-less `ChatClient`.

#### Scenario: Compose stage gets the history tool

- **WHEN** the compose stage builds a `ChatClient` for any podcast
- **THEN** the client has `searchPastEpisodes` registered as a callable tool

#### Scenario: Filter stage gets no tools

- **WHEN** the filter or score stage builds a `ChatClient`
- **THEN** the client has zero tools registered
