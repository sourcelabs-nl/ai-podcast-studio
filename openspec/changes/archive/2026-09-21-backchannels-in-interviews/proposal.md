## Why

The two Dutch two-host shows we take as reference do not hold attention through fast turn-taking. Both run on long, largely uninterrupted stretches of one speaker, minutes at a time in the first reference, 15 to 45 seconds typically in the second. What makes those stretches listenable is a device we do not have: the other host drops in a token of pure listening every 10 to 20 seconds, "ja", "hm hm", and the speaker simply carries on.

Our episodes are not long-winded by comparison. Episode 208 measured an expert median of 433 characters a turn, roughly 25 to 30 seconds, so the gap with the reference is not turn length. It is that every handover in our scripts costs a real exchange: `SPONTANEOUS INTERRUPTIONS` asks for 4 to 5 substantive interruptions, and there is nothing between "take the floor" and "say nothing".

The device is structurally impossible today. A backchannel only works if the interrupted speaker then resumes, which means two turns by one speaker with a short one in between, and the prompt forbids that in capitals as a rule overriding every other instruction.

Nothing in the pipeline requires the prohibition. `DialogueScriptParser` takes each turn's role from its own opening tag, and `InworldTtsProvider` resolves a voice per turn, so two turns of one role are two chunks in the same voice. The rule protects the writing, not the plumbing, and it is stated far more absolutely than the writing needs.

## What Changes

- The interview prompt SHALL ask for 2 to 3 backchannels per episode: a few words of pure listening from the interviewer, carrying no question, no new information and no topic change, after which the expert resumes their thought in a turn of their own.
- The structural alternation rule SHALL admit exactly one exception, the backchannel, and SHALL continue to forbid consecutive same-speaker turns that continue a point or evade the turn length rule.
- The turn length cap is unchanged. A backchannel is what lets an idea run longer without becoming a monologue.

## Capabilities

### Modified Capabilities

- `interview-composition`: gains the backchannel device and a narrowed alternation rule.

## Impact

- Backend: `InterviewComposer.buildPrompt` only. The dialogue composer is untouched.
- Tests: `InterviewComposerTest` gains 2 cases.
- No schema, API, frontend, or configuration change. Takes effect on the next generated episode.
