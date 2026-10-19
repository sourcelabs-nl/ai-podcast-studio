## ADDED Requirements

### Requirement: Chat models use only the resolved provider config

A chat model SHALL be built from the provider config resolved for the user and provider alone,
for both its synchronous and asynchronous client. Building it SHALL NOT read `OPENAI_API_KEY` or
any other environment variable, so a missing variable cannot fail a stage whose stored key is valid.

#### Scenario: No OPENAI_API_KEY in the environment
- **WHEN** a model is built for a stored OpenRouter config and `OPENAI_API_KEY` is unset
- **THEN** the model is built without error
