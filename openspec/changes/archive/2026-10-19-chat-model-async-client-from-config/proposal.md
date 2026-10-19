# Proposal

## Why

Every LLM stage of episode 296 failed with "At least one credential source must be specified: credential (apiKey), workloadIdentity, or adminApiKey" although the stored OpenRouter key was valid. Spring AI 2.0 builds an async OpenAI client for every chat model it is not handed one for, and takes its key only from the `OPENAI_API_KEY` environment variable; the process had none.

## What Changes

- `ChatClientFactory` passes Spring AI an async client built from the same stored provider config as the sync client (key, base URL, stage timeout), so no chat model depends on environment variables.

## Capabilities

### New Capabilities

### Modified Capabilities
- `user-api-keys`: adds "Chat models use only the resolved provider config".

## Impact

- `ChatClientFactory`, `OpenAiClientSupport.buildOpenAiClientAsync`. TTS is unaffected: its speech model builds no async client.
