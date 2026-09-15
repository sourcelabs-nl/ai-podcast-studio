---
okf_version: "0.2"
type: rule-rationale
title: A backchannel is what lets an idea run long
status: stable
source: src/main/kotlin/com/aisummarypodcast/llm/InterviewComposer.kt, BACKCHANNELS
generated:
  by: claude-opus-5
  at: 2026-09-15T00:00:00Z
---

Between "take the floor" and "say nothing" the prompt had nothing. It asks for 4
to 5 substantive interruptions an episode, so every handover costs a real
exchange, and a thought that needs longer than one turn cannot get it without
becoming a monologue.

The reference shows solve this with a token of pure listening: a few words, no
question, no new information, and the speaker simply continues. See
[[turn-length-in-the-reference-shows]] for why that, and not turn length, is the
difference between them and us.

The device requires two turns by one speaker with a short one between them,
which the alternation rule forbade in capitals, claiming it would break TTS. It
does not: `DialogueScriptParser` reads each turn's role from its own opening tag
and `InworldTtsProvider` resolves a voice per turn, so two turns of one role are
two chunks in one voice. The false reason was removed rather than softened, and
the rule now names the backchannel as its single exception, still forbidding a
same-speaker pair that continues a point or evades the length cap.

Kept to two or three an episode. There is little room to vary a token that is
short by definition, so at any higher rate it reads as a tic rather than as
listening.

Proposal and reasoning: OpenSpec change `backchannels-in-interviews`.

Related: [[adjacent-turns-must-not-repeat]], [[echo-turns]]
