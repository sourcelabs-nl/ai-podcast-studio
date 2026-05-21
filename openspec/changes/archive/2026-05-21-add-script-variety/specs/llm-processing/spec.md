## ADDED Requirements

### Requirement: Compose call passes temperature

The compose stage SHALL include `temperature` in the `OpenAiChatOptions` passed at call time, sourced from `podcast.composeTemperature` or the system default `0.95`. Filter and score stages MAY continue to omit temperature.

#### Scenario: Options include temperature

- **WHEN** the compose stage invokes the LLM
- **THEN** the `OpenAiChatOptions` instance carries the resolved temperature value
