## Context

Multi-speaker scripts reach TTS as free text carrying XML-style speaker tags (`<interviewer>…</interviewer>`). `DialogueScriptParser` splits those tags into turns and `InworldTtsProvider` maps each turn's role to a voice, so a script the parser cannot read produces zero turns and a hard failure at the last stage of the pipeline, after compose has already been paid for.

Three guards sit between compose and TTS, and an untagged script passed all three:

- `RoleTagValidationAdvisor` collected tags matching `SPEAKER_TURN_PATTERN` and kept those outside the allowed roles. An untagged script yields an empty set, which read as valid.
- `normalizeSquareBracketSpeakerTags` recovers only a `[expert]…</expert>` opener, and there were no square brackets either.
- `stripOutsideSpeakerTags` deliberately returns an untagged script unchanged, because that is the legitimate briefing case.

Episode 187 hit exactly this: `z-ai/glm-5.3` returned a well-written, correctly alternating interview with every tag omitted. The failure is also unrecoverable by retry, since retry resumes at the failed stage and re-parses the stored script, so the only route back is a fresh compose.

The advisor is registered only by `DialogueComposer` and `InterviewComposer`. That is what makes a missing-tag check safe to put there: it never sees a briefing script.

## Goals / Non-Goals

**Goals:**

- Catch a compose response the TTS parser cannot read while the LLM call is still in flight, so it costs a retry rather than a failed episode.
- Reuse the existing validate → feed the error back → retry loop instead of adding a second mechanism.
- Give the model a correction specific enough to act on.
- State the tag requirement in the prompt, so the format has an explicit anchor rather than only a rule about which delimiters to use.

**Non-Goals:**

- Reconstructing speaker turns from an untagged script. Inferring turn boundaries from paragraph breaks would guess at which voice speaks which line, and a wrong guess ships as audio.
- Validating anything else about the script (length, structure, content). Those belong to other stages.
- Changing how briefing/monologue scripts are handled.
- Making a failed episode recoverable by retry. That would mean re-entering the pipeline at compose rather than the failed stage, which is a larger change to retry semantics.

## Decisions

**Validation returns a described problem, not a set of bad roles.** The advisor previously threaded `Set<String>` of invalid roles through the loop, and every message was built from it. With two failure modes, a private `TagProblem(summary, correction)` carries both the log/exception wording and the model-facing correction, and the retry loop stays identical for both. The alternative, a boolean flag plus branching at each message site, would have spread the two modes across the loop body.

**A wrong tag is reported ahead of a missing one.** A response carrying only `<function_results>` satisfies both checks. Naming the offending tag gives the model more to correct than telling it the script is untagged, and this ordering keeps the pre-existing episode-163 behaviour byte-identical, so the existing scenario and its test are unaffected.

**The presence check is deliberately loose.** It looks for an opening tag of an allowed role in either angle-bracket or square-bracket form, and does not require a matching closer. `normalizeSquareBracketSpeakerTags` recovers the square-bracket form downstream and `SPEAKER_TURN_PATTERN` tolerates an unclosed opener, so a stricter check would retry a script the pipeline can already voice, costing a compose to fix nothing. The alternative considered was calling `normalizeSquareBracketSpeakerTags` inside the advisor and counting real turns; that is more precise but double-logs its "recovered N characters" warning, once in the advisor probe and once in the composer.

**The prompt rule is added to `buildSpeakerTagFormatBlock` rather than to each composer.** That block is already shared by the dialogue and interview prompts, so both get identical wording from one place, consistent with how every other cross-composer rule in `ComposerUtils` is defined.

## Risks / Trade-offs

- **A legitimately tagged script is rejected and re-composed, wasting minutes and tokens** → The check passes if any allowed role appears as an opener in either delimiter form, so it fires only when the model has produced no recognisable tag anywhere. A scenario pins the square-bracket case specifically.
- **Three failed attempts now cost three composes instead of one** → Bounded by the existing `maxRetries = 2`, and it only triggers where the previous behaviour was a guaranteed episode failure, so the worst case trades a wasted compose for a chance at recovery.
- **The retry may not fix it if the model is systematically ignoring the format** → The advisor then throws at the compose stage with a message naming the cause, instead of surfacing as an opaque parser error at TTS. Diagnosing it is the improvement; the episode still fails.
- **The prompt change alters the `CachingChatModel` key** → Cached responses for prompts built before this change will not be reused, so the first compose per distinct prompt pays full price once. This is desirable here: it also means the cached untagged response from episode 187 cannot be replayed.

## Migration Plan

No data migration, no schema change, no configuration change. The behaviour ships with the application build; restart is sufficient. Rollback is reverting the two source files, which restores the previous validation exactly, since the ordering decision leaves the invalid-tag path unchanged.

## Open Questions

- Why the model dropped its tags on this run, when the same prompt and model produced correctly tagged scripts for episodes 182-186, is not established. The compose prompt is dense with rules and the format had no hard-requirement statement anchoring it, which is the motivation for the prompt change, but that is a hypothesis rather than a confirmed cause.
- Whether retry should re-enter the pipeline at compose for stage failures that a stored artifact can never satisfy is left open; today retry resumes at the failed stage and cannot fix this class of failure.
