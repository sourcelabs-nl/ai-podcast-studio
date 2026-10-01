## Why

Episode 280 of The Daily Agentic AI Podcast was published as a 10-second clip in which the expert asks for "the original script". The first compose attempt came back without speaker tags. The retry told the model to "rewrite the ENTIRE script", but the rejected response was not part of the retry conversation, so the model had nothing to rewrite and said so. That refusal sat inside a valid `<expert>` tag and passed every validation check.

## What Changes

- A speaker-tag retry sends the rejected response back as an assistant message, followed by the correction as a new user message, instead of appending the correction to the original user message.
- Validation rejects a multi-speaker script in which fewer than two speakers talk (capped at the number of configured roles), and retries it with a correction that asks for the complete episode from the source material.

## Capabilities

### New Capabilities

### Modified Capabilities
- `dialogue-composition`: speaker-tag validation adds a minimum-speaker check and the retry carries the rejected response.

## Impact

- `llm/RoleTagValidationAdvisor.kt` and its test. Used by the dialogue and interview composers; briefing is unaffected.
- A retry now also sends the rejected response as input tokens.
