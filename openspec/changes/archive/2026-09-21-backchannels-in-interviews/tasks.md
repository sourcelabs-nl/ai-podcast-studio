## 1. Prompt

- [x] 1.1 A `BACKCHANNELS` rule in `InterviewComposer.buildPrompt`, placed with the other engagement techniques and ahead of the turn length rule
- [x] 1.2 Narrow the structural alternation rule to admit the backchannel and drop the false claim that a same-speaker pair breaks TTS

## 2. Verification

- [x] 2.1 Confirm `DialogueScriptParser` and `InworldTtsProvider` handle consecutive same-role turns
- [x] 2.2 `InterviewComposerTest` covers the new rule and the narrowed exception
- [x] 2.3 Full suite green
