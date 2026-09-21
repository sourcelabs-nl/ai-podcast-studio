## Context

Compose output is free text carrying XML-ish speaker tags, parsed by one regex (`SPEAKER_TURN_PATTERN`) that three separate steps rely on: `stripOutsideSpeakerTags` to find the script's span, `RoleTagValidationAdvisor` to police role names, and the TTS provider to split turns. A turn the regex cannot see is therefore invisible to validation *and* to trimming, and trimming's contract ("drop everything before the first turn") turns that invisibility into deletion.

That coupling is what made episode 184's single mis-typed bracket expensive. The tag was wrong in one character, in a way a human reading the raw output would not even register, and the pipeline's response was to silently discard 728 characters of finished, spoken content.

## Goals / Non-Goals

**Goals:**

- A turn the model closed correctly is not lost because it opened it with the wrong bracket.
- Discarding spoken content is never silent.
- The model is told the rule, so recovery is the exception rather than the mechanism.

**Non-Goals:**

- A tolerant, general-purpose speaker-tag parser. Loosening `SPEAKER_TURN_PATTERN` itself would change what every consumer sees, including `RoleTagValidationAdvisor`, whose whole job is to fail closed on tags it does not recognise. A narrow normalization ahead of the existing pattern keeps that guarantee intact.
- Handling `[role]…[/role]` (both delimiters square). It has never occurred, and unlike the observed case there is no angle-bracket closer to anchor the rewrite against, so the test that makes this fix safe would not apply. The prompt rule names it so the model is warned off it anyway.
- Reconstructing an opening the model never wrote. If the intro is genuinely absent, that is a prompt-adherence problem, not a parsing one.

## Decisions

**Rewrite the opener rather than loosen the pattern.**

Considered making `SPEAKER_TURN_PATTERN` accept either bracket, e.g. `[<\[](\w+)[>\]]`. Rejected: the pattern is shared with `RoleTagValidationAdvisor`, and a looser pattern there would start matching square-bracketed delivery cues as speaker tags, so every `[warm and conversational]` would be reported as an invalid role and trigger the advisor's retry loop. Normalizing before the pattern runs confines the change to one well-named function.

**Gate the rewrite on "the next `<` is my own closing tag".**

The obvious gate is "there is a matching closer somewhere later", but that is unsafe: given `[expert] unterminated <interviewer>Q?</interviewer><expert>A.</expert>`, the closer it finds belongs to a different, well-formed turn, and rewriting would nest one turn inside another and corrupt more than it fixed. Requiring the *very next* `<` to begin the role's own closing tag encodes the real invariant, which is that a speaker turn's body contains no tags. It accepts the observed case (the body was plain prose) and refuses every shape where the closer is not actually this turn's.

Implemented as an index scan rather than a lookahead regex. The regex form (`\[$role\](?=((?!<$role>).)*?</$role>)`) expresses the same rule but is far harder to read, and this file is shared prompt-and-parsing plumbing that should stay legible.

**Warn on substantial discards, not on all of them.**

`stripOutsideSpeakerTags` exists because the model reliably emits a short "I have enough context. Writing the script now." preamble; warning on that would be noise on every episode. The threshold is two conditions, either of which is enough: more than 200 characters, or the presence of a closing tag. The second is the precise signature of a dropped turn and would have caught episode 184 on the day it happened, regardless of length.

**Keep normalization in the composers, not in `stripOutsideSpeakerTags`.**

Trimming has no idea which roles are legal; the composers do (`INTERVIEW_ROLES`, `resolveSpeakerRoles(podcast)`). Passing roles into the trim function to serve one caller's needs would widen its signature for no gain, and the two operations are genuinely separate: one repairs a tag, the other removes untagged text.

## Risks / Trade-offs

- **A script that legitimately speaks the literal text `[expert]` followed by prose and then `</expert>` would be rewritten.** → Not a realistic script: the roles are drawn from the podcast's configured TTS voices, and a turn body containing the words "[interviewer]" verbatim is not something a spoken-word prompt produces.
- **The recovered turn keeps the space the model left after the bracket, so the body starts `<interviewer> Eighty five percent`.** → Cosmetic; whitespace at a turn boundary cannot change synthesis. Trimming turn bodies is a separate concern and not worth coupling to this fix.
- **The prompt gains another rule, and prompt budget is finite.** → One sentence, and it replaces a failure mode that costs a whole episode opening. The `unsteer-cold-open` change set the same precedent: fix at the source, keep a mechanical backstop.
- **Normalization masks the model's mistake, so recurrence is only visible in logs.** → Intentional, and why each rewrite logs at WARN rather than at debug. The alternative, failing the compose, would trade a silently damaged episode for a loudly missing one.
