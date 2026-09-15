## Why

The compose prompt calls two consecutive turns by the same speaker "ABSOLUTELY FORBIDDEN", in capitals, and says the rule overrides every other instruction. Nothing enforces it. `RoleTagValidationAdvisor` checks that no tag falls outside the allowed roles and that the script carries a speaker tag at all; alternation is checked nowhere, by the advisor or after it.

Episode 209 shipped turns 12 and 13 both on the interviewer, through TTS and out to the feed, with nothing in the log. The two turns also said nearly the same thing, because the prompt rule that produced them made the model transition twice; that wording has since been fixed, but the fix is another prompt rule, and a prompt is not a validator. The invariant the TTS pipeline is written against still rests on the model choosing to honour it.

A re-prompt is the wrong instrument here. The other recoveries in `ComposerUtils` repair rather than re-ask for exactly this reason: compose takes minutes, and a full rewrite discards a script that is wrong in one adjacency and right everywhere else, including the attention devices it was asked for.

## What Changes

- `ComposerUtils.mergeConsecutiveSameSpeakerTurns` SHALL join adjacent turns of the same role into one turn and SHALL log what it merged, so the defect is visible rather than silent.
- It SHALL run after `closeUnterminatedFinalTurn` and before `stripOutsideSpeakerTags` in both `InterviewComposer` and `DialogueComposer`, so a turn recovered by an earlier step takes part in the check.
- It SHALL merge only turns whose role is one of the podcast's own roles and that are separated by whitespace alone, leaving anything else for the existing steps to handle.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `compose-script-cleanup`: gains the merging of consecutive same-speaker turns.

## Impact

- Backend: new `mergeConsecutiveSameSpeakerTurns` in `ComposerUtils.kt`, wired into `InterviewComposer.kt` and `DialogueComposer.kt`.
- Tests: `ScriptCleanupTest` gains cases for the merge and for each case it must leave alone.
- No schema, API, frontend, or configuration change.
