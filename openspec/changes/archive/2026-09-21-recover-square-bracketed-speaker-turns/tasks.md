<!-- Implemented before this change was written; every task below is already done. -->

## 1. Recover the mis-typed opener

- [x] 1.1 Add `normalizeSquareBracketSpeakerTags(script: String, roles: Set<String>)` to `ComposerUtils`, rewriting `[role]` to `<role>` only when the next `<` after the opener begins that role's closing tag
- [x] 1.2 Log each rewrite at WARN with the role and the number of characters recovered
- [x] 1.3 Document why the narrow gate is the safe one, naming the nesting case it refuses
- [x] 1.4 Call it from `InterviewComposer` with `INTERVIEW_ROLES`, before `stripOutsideSpeakerTags`
- [x] 1.5 Call it from `DialogueComposer` with `resolveSpeakerRoles(podcast)`, before `stripOutsideSpeakerTags`

## 2. Stop discarding script content silently

- [x] 2.1 Add a file-level logger to `ComposerUtils`
- [x] 2.2 Add `warnIfSubstantial(position, discarded)` and the `EXPECTED_META_COMMENTARY_LENGTH` constant
- [x] 2.3 Call it for the text discarded before and after the turn span in `stripOutsideSpeakerTags`, and say explicitly when the discarded run holds a closing tag
- [x] 2.4 Extend the function's KDoc to record that it used to delete spoken content without a word

## 3. Tell the model the rule

- [x] 3.1 Add shared `buildSpeakerTagFormatBlock(roles)` to `ComposerUtils`, banning square brackets on either side of a speaker tag and explaining that such a turn is lost
- [x] 3.2 Include it in the interview prompt and the dialogue prompt

## 4. Tests

- [x] 4.1 `ScriptCleanupTest`: a square-bracketed opener is rewritten
- [x] 4.2 `ScriptCleanupTest`: episode 184's shape survives normalize-then-trim with all 3 turns intact
- [x] 4.3 `ScriptCleanupTest`: pin the pre-fix behaviour, trimming alone still drops the square-bracketed opener
- [x] 4.4 `ScriptCleanupTest`: a `[warm and conversational]` delivery cue is left alone
- [x] 4.5 `ScriptCleanupTest`: a role outside the podcast's configured set is left alone
- [x] 4.6 `ScriptCleanupTest`: an opener with no closing tag is left alone
- [x] 4.7 `ScriptCleanupTest`: an opener whose closer belongs to a later well-formed turn is left alone
- [x] 4.8 `ScriptCleanupTest`: several mis-typed openers in one script are all rewritten
- [x] 4.9 `ScriptCleanupTest`: an empty role set returns the script unchanged
- [x] 4.10 Run `mvn test` and confirm the whole suite passes (1174 tests, 0 failures)

## 5. Verify against the real failure and repair episode 184

- [x] 5.1 Run episode 184's raw cached compose response (`llm_cache` row 20891) through the fixed path: 15,524 to 16,252 characters, 66 to 67 turns, podcast name, date and sponsor read restored, delivery cue untouched
- [x] 5.2 Confirm the corrected script is a pure prepend, `new.endsWith(current)` is true, so nothing else in the episode changed
- [x] 5.3 Back up the database, restore episode 184's `script_text` (one row), and confirm the stored script now opens with the interviewer turn
- [x] 5.4 Regenerate the audio and confirm the TTS provider reports `turn 1/67 (role: interviewer)`, 975 seconds, 14,706 characters
- [ ] 5.5 Re-publishing episode 184 to SoundCloud and FTP is deliberately not done here; both still hold the short version
