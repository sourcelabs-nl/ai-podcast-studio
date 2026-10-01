## Context

`RoleTagValidationAdvisor` validates dialogue and interview scripts and re-issues the request with a correction when a check fails. Before this change it did that through `Prompt.augmentUserMessage`, which appends to the original user message and drops the rejected response.

## Decisions

- **Rejected response as an assistant turn.** The retry prompt is the original messages, then `AssistantMessage(rejected)`, then `UserMessage(correction)`. This is the usual self-correction pattern, and it is what makes "rewrite the script" mean something. The alternative, quoting the rejected text inside the correction, mixes the model's own output with instructions.
- **Minimum of two speakers, capped by the configured roles.** A dialogue or interview always has at least two speakers. The check runs on the cleaned script after the structure check, so it only fires on a script that is otherwise usable. It uses `minOf(2, allowedRoles.size)` so a one-role configuration never fails.

## Risks / Trade-offs

- The rejected response is sent back as input, which adds its size to the retry's input tokens. A retry is rare and the cost is small next to publishing an empty episode.
- A single-speaker reply now costs a retry, and fails the compose after the retries are exhausted instead of publishing.
