## Why

Episode 184 (21 August 2026) was published without its cold open. The listener heard the expert begin mid-thought: `[warm and conversational] And what a week to close out. That number comes from a GitHub issue...`, where "that number" referred to a figure never spoken.

The compose model had written the opening turn, and written it well: the hook, the interviewer's name, the podcast name, the date, the sponsor read, and the teaser. It made one mistake, on one character. It opened the turn with a square bracket while still closing it with an angle bracket:

```
[interviewer] Eighty five percent. ... quietly took over ChatGPT.</interviewer>
<expert>[warm and conversational] And what a week to close out. ...</expert>
```

`SPEAKER_TURN_PATTERN` is `<(\w+)>.*?</\1>`, so it requires an angle-bracket opener and never matched that turn. Everything downstream then treated 728 characters of finished script as junk: `stripOutsideSpeakerTags` reads text before the first *matching* turn as the model thinking out loud and drops it, and `RoleTagValidationAdvisor` scans with the same pattern, so it saw no tag to object to. 67 turns became 66 with nothing written to the log.

The square bracket is not a random slip. Compose prompts are dense with square-bracketed delivery cues, and the very next turn opens with `[warm and conversational]`, so the model's own bracket habit bled into the tag beside it.

Rare but not harmless: 1 of 140 tagged episodes lost its introduction this way, and the loss was silent, so it surfaced only when a listener noticed the audio.

## What Changes

- New `normalizeSquareBracketSpeakerTags(script, roles)` rewrites `[role]` to `<role>`, applied by the dialogue and interview composers before trimming. It is deliberately narrow: only the podcast's configured speaker roles are considered, and an opener is rewritten only when the very next `<` in the script begins that role's own closing tag. A speaker turn's body carries no tags, so that test identifies the mis-typed opener while leaving a genuine delivery cue such as `[warm and conversational]` untouched and refusing to swallow a later, well-formed turn. Each recovery is logged at WARN.
- `stripOutsideSpeakerTags` SHALL log at WARN when it discards more than a scrap of text, and SHALL say so explicitly when the discarded run contains a closing tag, since that means a whole malformed turn was thrown away. It was previously silent about deleting spoken content, which is why this went unnoticed.
- New shared `buildSpeakerTagFormatBlock(roles)` tells the model that speaker tags take angle brackets on both sides and that square brackets are reserved for delivery cues. Included in the dialogue and interview prompts, so the common case is fixed at the source and the normalization is a backstop.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `compose-script-cleanup`: the trimming requirement gains the square-bracket recovery that runs before it and the WARN on substantial discards.

## Impact

- Backend: `ComposerUtils` (new `normalizeSquareBracketSpeakerTags` and `buildSpeakerTagFormatBlock`, a file-level logger, and a `warnIfSubstantial` helper used by `stripOutsideSpeakerTags`); `InterviewComposer` and `DialogueComposer` (normalize before trim, and include the new prompt block).
- Tests: `ScriptCleanupTest` gains 9 cases covering the recovery, the narrowness guards, and the pre-fix behaviour it replaces.
- No schema, API, frontend, or configuration change.
- Episode 184 was repaired out of band. Its raw compose response was still in `llm_cache` (`app.llm-cache.max-age-days` is unset, so `LlmCacheCleanup` never expires anything), and running it through the fixed path reproduced the full 67-turn script, which is the current script with the lost turn prepended and nothing else altered. The script was restored and the audio regenerated.
