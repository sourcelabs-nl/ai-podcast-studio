<!-- Implemented before this change was written; every task below is already done. -->

## 1. Recover the turn

- [x] 1.1 Add `closeUnterminatedFinalTurn(script, roles)` to `ComposerUtils`, returning the script unchanged in every ambiguous case
- [x] 1.2 Log the role and the recovered length at WARN
- [x] 1.3 Call it ahead of `stripOutsideSpeakerTags` in `InterviewComposer` and `DialogueComposer`
- [x] 1.4 Correct the `RoleTagValidationAdvisor` KDoc, which claimed the turn pattern tolerates an unclosed opener

## 2. Tests

- [x] 2.1 `ScriptCleanupTest`: an unclosed final turn is closed and survives stripping
- [x] 2.2 `ScriptCleanupTest`: a fully closed script is unchanged
- [x] 2.3 `ScriptCleanupTest`: trailing prose with no role tag, an empty body, and a tail with further role tags are all left alone
