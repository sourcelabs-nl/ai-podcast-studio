## Context

The reference analysis was read the wrong way round at first. A YouTube transcript is segmented into caption cues of about 6.6 words, which looks like rapid short turns and is nothing of the sort: it is caption chunking. Read as speech, both reference shows favour long single-speaker stretches. So the conclusion is not "shorten our turns", which the measurements contradict, but "give a long stretch the support that makes it listenable".

## Decisions

**The exception is scoped to the device, not to alternation in general.**

Relaxing the alternation rule outright invites the failure it was written against: the expert continuing across two turns to get past the 3-4 sentence cap. The rule therefore still forbids a same-speaker pair that continues a point, and names the backchannel as the only shape in which one may occur. A rule with one named exception is still enforceable by a reader; a rule with none was being broken anyway, unnoticed, in episode 209.

**The prohibition was never a pipeline constraint.**

The prompt claims two consecutive same-speaker tags "will break the TTS pipeline". That is not true and was worth checking before relaxing it: `DialogueScriptParser` reads each turn's role from its own opening tag and ends the turn at the next tag token, and `InworldTtsProvider` maps turn to voice individually. Two turns of one role synthesise as two chunks in one voice, concatenated like any other pair. The claim is removed rather than softened, because a false reason in a prompt is a rule the model can be argued out of.

**Kept rare on purpose.**

Two or three an episode. A backchannel that appears at every opportunity stops reading as listening and becomes a verbal tic, and unlike the interruption categories there is no way to vary it much: the tokens are short by definition.

**Not applied to the dialogue composer.**

Two hosts of equal standing do not backchannel the same way; the device belongs to an asymmetric conversation where one party is explaining. Extending it there is a separate decision with its own evidence.

## Non-Goals

- Changing the turn length cap. The measurements do not support shortening it, and lengthening it is the monologue the backchannel exists to avoid.
- Raising the episode word target. Episodes already overshoot the configured 1500 words by 40 to 100 percent, so the target is not what governs length.
