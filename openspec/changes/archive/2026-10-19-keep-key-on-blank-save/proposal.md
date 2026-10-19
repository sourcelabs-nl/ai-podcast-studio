# Proposal

## Why

Saving a provider config with an empty key field encrypted the empty string over the stored key. The config still looked present, so neither the environment fallback nor any validation applied, and every LLM stage failed at runtime.

## What Changes

- `setConfig` treats a blank key like an absent one and keeps the stored key.

## Capabilities

### New Capabilities

### Modified Capabilities
- `user-api-keys`: adds "A blank key keeps the stored key".

## Impact

- `UserProviderConfigService.setConfig`.
