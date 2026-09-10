## Context

`stripOutsideSpeakerTags` is a whitelist: what `SPEAKER_TURN_PATTERN` does not match is dropped. That is the right default for prose the model leaks around the script, and the wrong one for a turn that is complete except for its closing tag.

## Decisions

### Recover rather than re-prompt

A missing final closer is unambiguous when the tail opens with a known role and contains no other role tag: the model wrote the turn and forgot to close it. Adding the closer costs nothing and keeps the episode whole. Re-prompting would spend another compose call (the most expensive in the pipeline) on a script that is already correct in substance.

Anything less clear-cut is left alone. If the tail contains further role tags the model produced something structurally wrong, and guessing where the turns divide would invent content.

### Log the recovery

The repair is silent in the output, so it is logged at WARN with the role and the number of characters recovered. A model that starts doing this routinely is then visible, rather than hidden behind a script that happens to come out complete.

## Risks

- A tail that opens with a role tag but is genuinely leaked commentary would be turned into a spoken turn. Judged unlikely: leaked commentary does not begin with the podcast's own role tag.
