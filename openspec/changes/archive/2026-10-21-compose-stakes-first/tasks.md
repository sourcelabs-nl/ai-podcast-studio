# Tasks

## 1. Shared rules

- [x] 1.1 Add `buildStakesBlock` and `buildSoWhatBlock` to `ComposerUtils` and include the stakes block in the briefing, dialogue and interview prompts. Verify with `BriefingComposerTest` `buildPrompt puts a topic's stakes before its mechanism`, `DialogueComposerTest` and `InterviewComposerTest` `prompt puts a topic's stakes before its mechanism`.
- [x] 1.2 Include the so-what block in the dialogue and interview prompts only. Verify with `DialogueComposerTest` and `InterviewComposerTest` `prompt asks for a so-what question per segment instead of bare bridges`, and the `assertFalse` on "ASK SO WHAT" in the briefing test.

## 2. Interview and dialogue prompts

- [x] 2.1 Cast the interviewer as a curious, skeptical colleague and cap backchannels at 2. Verify with `InterviewComposerTest` `interviewer is cast as a skeptical colleague with at most two backchannels`.
- [x] 2.2 Remove the fixed 60-90 second segment length. Verify with `DialogueComposerTest` and `InterviewComposerTest` `prompt no longer holds every segment to a fixed duration`.

## 3. Rapid-fire tier

- [x] 3.1 Change the rapid-fire instruction to "up to N", allow grouping and dropping, keep the segment mandatory. Verify with the updated `SubtopicPlanTest` rapid-fire assertions (`up to 2 items`, grouping allowance, no "do NOT skip any item", "Do NOT skip the rapid-fire segment").

## 4. Verification

- [x] 4.1 `mvn test` passes.
