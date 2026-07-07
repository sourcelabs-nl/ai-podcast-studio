## Context

`DialogueComposer`/`InterviewComposer` call `chatClient.prompt().user(prompt).call().chatResponse()` and take the raw response text directly (`.chatResponse()`, not `.entity()`). The script is free-text with `<role>...</role>` XML-style tags, ~1000+ words, produced under heavy narrative-directive prompting (interruptions, cliffhangers, callbacks, humor). `ComposerUtils.stripOutsideSpeakerTags` already trims leading/trailing non-tagged text, and `TopicOrderExtractor` pulls a `|||TOPIC_ORDER|||` marker block out of the same text — but nothing validates that every tag used is actually one of the podcast's configured speaker roles. `DialogueScriptParser` (TTS-side) is deliberately lenient and accepts any tag name as a role, so an unexpected tag only surfaces as a failure once the TTS provider tries to look up a voice for it.

Spring AI 2.0 ships `validateSchema()` (`StructuredOutputValidationAdvisor`, confirmed by decompiling `spring-ai-client-chat-2.0.0.jar`): on typed/JSON output via `.entity(...)`, it validates the response against the type's JSON Schema and, on failure, automatically re-prompts the model with the validation error appended, retrying up to a configurable count (default 3 total attempts, confirmed from the advisor builder's bytecode). This is exactly the self-correcting mechanism we want, but it only applies to JSON output.

## Goals / Non-Goals

**Goals:**
- Catch an invalid speaker tag (e.g. a leaked tool-call artifact like `<function_results>`) at the compose stage, before it reaches TTS.
- Give the model a bounded, LLM-visible chance to self-correct, mirroring `validateSchema()`'s retry semantics, rather than failing on the first bad tag.
- Fail loudly with a clear, actionable error if the model can't self-correct, instead of silently passing a bad tag downstream.
- Keep the existing free-text, XML-tagged script format and the heavily-tuned narrative prompt unchanged.

**Non-Goals:**
- Migrating compose output to typed/JSON (`.entity()` + `validateSchema()`) to use Spring AI's built-in advisor directly. Rejected: forcing a ~1000-word creative script with many narrative directives into a JSON schema risks truncation/escaping failures on long responses from weaker models (e.g. `z-ai/glm-5.2`, the model involved in episode 163) — a worse failure mode than the one being fixed.
- Changing `DialogueScriptParser`, the TTS providers, or the frontend script renderer. They keep consuming the same tagged-text format; this change only prevents a bad tag from ever reaching them.
- Validating anything about tag *content* (only tag *names* against the allowed role set).

## Decisions

**Custom `CallAdvisor` instead of `.entity()`/`validateSchema()`.** `RoleTagValidationAdvisor` implements Spring AI's `CallAdvisor` interface directly and is attached per-call via `.advisors(...)`. It replicates `StructuredOutputValidationAdvisor`'s exact re-entry pattern (`callAdvisorChain.copy(this).nextCall(request)`, `ChatClientRequest.mutate().prompt(...).build()`, `Prompt.augmentUserMessage(String)`) but validates extracted tag names against a `Set<String>` of allowed roles instead of a JSON Schema against a parsed object. This was validated empirically: a throwaway MockK-based test (mocking only `ChatModel.call(Prompt)`, letting the real `ChatClient`/advisor chain run) confirmed `validateSchema()` behaves as expected for JSON, before we decided the free-text approach was the safer fit here and built the analogous advisor for it.

**Retry budget: 1 initial attempt + 2 retries (3 total), matching Spring AI's own default.** Chosen for consistency with the mechanism this mirrors, not derived from data on how often `z-ai/glm-5.2`-class leaks recur.

**Allowed-role sets stay call-site-specific, not centralized in `Podcast`.** `DialogueComposer` derives roles via the new `resolveSpeakerRoles(podcast)` helper (dialogue roles are podcast-configured, via `ttsVoices` keys); `InterviewComposer` uses a fixed `setOf("interviewer", "expert")` constant, since interview format is a hardcoded two-role shape by design, not per-podcast configurable. `resolveSpeakerRoles` exists specifically so `DialogueComposer`'s prompt-building and the advisor's validation set can't drift apart.

**Reused, not duplicated, the tag-extraction regex.** `SPEAKER_TURN_PATTERN` (`ComposerUtils.kt`) was widened from `private` to `internal` so `RoleTagValidationAdvisor` (same module, different file) can reuse it rather than maintaining a second copy of the same pattern.

## Risks / Trade-offs

- **Tool-call interaction**: the compose `ChatClient` has tools registered (`HistoryLookupTool`, optionally `ResearchTool` via `createForCompose`). The advisor skips validation when `chatResponse.hasToolCalls()` is true, since a response mid-tool-call-round-trip isn't the final script text. In practice Spring AI's internal tool-calling loop resolves tool calls before a final response reaches advisors, so this branch is expected to rarely trigger — verified with a dedicated test, but not exercised against a live model with real tool calls. → Mitigation: if this ever proves incorrect in production (a script silently skips validation), the fix is confined to this one advisor.
- **Retry replays tool-call context, not just the prompt**: because the retry re-enters the same request/response history (rather than starting a fresh compose call), a self-correction round does not re-run `HistoryLookupTool`/`ResearchTool` from scratch — cheaper than this project's existing hand-rolled retry loops (e.g. `ArticleScoreSummarizer`), which redo the entire call with no prior context.
- **Non-determinism**: the leak that caused episode 163 is model-dependent and doesn't reliably reproduce on demand, so this was verified via (a) a unit test simulating the exact leaked-tag scenario and (b) a real end-to-end regenerate of the failed episode, which completed cleanly without triggering a retry (the model didn't leak that time). There is no repeatable live-model reproduction of the original failure.
