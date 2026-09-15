## Context

Every other speaker-tag defect in `ComposerUtils` is repaired in place: a square-bracketed opener, an unclosed final turn, meta-commentary outside the tags. Only a tag outside the allowed roles, or a script with no tags at all, is worth a re-prompt, because neither can be repaired without inventing content. Consecutive same-speaker turns belong to the first group, which is what decides the shape of this change.

## Decisions

**Merge rather than re-prompt.**

Two adjacent turns of one speaker carry no ambiguity about what was meant: the pipeline needs one turn, and the text of both is already there. Joining them loses nothing. Re-prompting costs a full compose, several minutes and a second bill, and returns a different script, so a run that was good apart from one adjacency is thrown away to fix the adjacency. That is the wrong trade at this severity.

The merge does not make the writing better. Two turns saying nearly the same thing become one turn saying it twice. The prompt rule is what stops them being written; this is the net that stops the structural violation reaching TTS unseen.

Alternative considered: extending `RoleTagValidationAdvisor`, which already re-issues. Rejected on the cost above, and because the advisor's two existing checks are both unrepairable failures, which is a distinction worth keeping.

**Narrow, like the recoveries around it.**

Only roles the podcast actually uses are merged, and only across whitespace. A tag outside the role set is the advisor's business and must still reach it; anything other than whitespace between two turns means the script is structurally wrong in some further way, which the surrounding steps already log and discard.

**Log at WARN.**

The point of the change is that the violation stopped being invisible. The merge is silent in the audio, so the log line is the only signal that the model broke a rule the prompt states in capitals, and it is what makes the frequency measurable.
