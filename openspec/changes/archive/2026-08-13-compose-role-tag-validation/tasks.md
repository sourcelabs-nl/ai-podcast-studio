## 1. Shared role-resolution helper

- [x] 1.1 Widen `SPEAKER_TURN_PATTERN` in `ComposerUtils.kt` from `private` to `internal`
- [x] 1.2 Add `resolveSpeakerRoles(podcast: Podcast): Set<String>` to `ComposerUtils.kt`, encapsulating the existing `ttsVoices` keys / `host`,`cohost` fallback

## 2. Role tag validation advisor

- [x] 2.1 Implement `RoleTagValidationAdvisor` (`CallAdvisor`) with configurable `allowedRoles` and `maxRetries` (default 2)
- [x] 2.2 Skip validation when the response has no `chatResponse` or still has pending tool calls
- [x] 2.3 Extract tag names via `SPEAKER_TURN_PATTERN` and compare against `allowedRoles`
- [x] 2.4 On an invalid tag, append an LLM-visible error via `Prompt.augmentUserMessage` and retry via `CallAdvisorChain.copy(this).nextCall(...)`
- [x] 2.5 Throw `IllegalStateException` naming the invalid role(s) after exhausting retries

## 3. Wire into composers

- [x] 3.1 `DialogueComposer.buildPrompt` uses `resolveSpeakerRoles(podcast)` instead of inlining the fallback
- [x] 3.2 `DialogueComposer.compose` attaches `RoleTagValidationAdvisor(resolveSpeakerRoles(podcast))` via `.advisors(...)`
- [x] 3.3 `InterviewComposer` adds a shared `INTERVIEW_ROLES` constant (`interviewer`, `expert`), used by both the prompt's valid-tags line and the advisor
- [x] 3.4 `InterviewComposer.compose` attaches `RoleTagValidationAdvisor(INTERVIEW_ROLES)` via `.advisors(...)`

## 4. Tests

- [x] 4.1 Valid-roles-on-first-try passes through without retry
- [x] 4.2 Invalid role retries once and self-corrects; retry prompt contains the bad tag and the allowed tags
- [x] 4.3 Response with pending tool calls bypasses validation
- [x] 4.4 Exhausting retries throws `IllegalStateException` naming the invalid role, after exactly `maxRetries + 1` model calls
- [x] 4.5 Full `mvn test` suite green (937 tests)

## 5. Verification

- [x] 5.1 Code review pass (code-reviewer agent) against architecture/kotlin-quality/spring-boot/spring-ai rules; addressed actionable findings (derive `InterviewComposer`'s valid-tags line from `INTERVIEW_ROLES`, add tool-call-bypass test coverage)
- [x] 5.2 Restart app (`./stop.sh && ./start.sh`) and regenerate the originally-failed episode (163 → new episode 164) end-to-end; confirmed `GENERATED` status, clean `interviewer`/`expert`-only tags, audio produced
