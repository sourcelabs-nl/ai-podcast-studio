## 1. Implementation

- [x] 1.1 `mergeConsecutiveSameSpeakerTurns(script, roles)` in `ComposerUtils.kt`, joining adjacent same-role turns and logging at WARN
- [x] 1.2 Wire it into `InterviewComposer` and `DialogueComposer`, after `closeUnterminatedFinalTurn` and before `stripOutsideSpeakerTags`

## 2. Tests

- [x] 2.1 Two adjacent turns merge, three merge into one, bodies keep their order
- [x] 2.2 An alternating script, a foreign role, and non-whitespace between turns are all left alone
- [x] 2.3 Full suite green
