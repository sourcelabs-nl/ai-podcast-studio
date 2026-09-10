## Why

Episode 202's audio stopped a fragment short of the end. TTS was not at fault: all 51 turns it was given were synthesised and concatenated. The 52nd turn never reached it.

The compose model closed every turn but the last, ending the script with `<interviewer>` and 517 characters of sign-off and no `</interviewer>`. `SPEAKER_TURN_PATTERN` matches an opener, a body and a closer, so the trailing text matched nothing, and `stripOutsideSpeakerTags` removes everything the pattern does not match. The final turn was silently deleted before the script was ever stored, and nothing downstream could tell that the episode was cut short.

A malformed opener is already re-prompted through `RoleTagValidationAdvisor`, but a *missing closer* on the last turn is not an invalid tag: the script reads correctly to a human and only the parser loses it.

## What Changes

- `ComposerUtils.closeUnterminatedFinalTurn` SHALL close a trailing speaker turn that carries a valid opener for one of the podcast's roles and no closer, and SHALL log what it recovered.
- It SHALL run before `stripOutsideSpeakerTags` in both `InterviewComposer` and `DialogueComposer`.
- It SHALL leave the script alone in every ambiguous case: no trailing text, text that opens with no role tag, an empty body, or a body containing further role tags (which means the model emitted something structurally wrong rather than dropping one closer).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `compose-script-cleanup`: gains the recovery of an unclosed final speaker turn.

## Impact

- Backend: new `closeUnterminatedFinalTurn` in `ComposerUtils.kt`, wired into `InterviewComposer.kt` and `DialogueComposer.kt`; a corrected KDoc on `RoleTagValidationAdvisor`, which claimed `SPEAKER_TURN_PATTERN` tolerates an unclosed opener.
- Tests: `ScriptCleanupTest` gains 6 cases.
- No schema, API, frontend, or configuration change.
