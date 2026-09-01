## 1. Suppress flattening instructions

- [x] 1.1 Add the flattening/distorting instruction word set to `InworldScriptPostProcessor`
- [x] 1.2 Add `flattensDelivery(tag)` matching on whole words within the instruction
- [x] 1.3 Strip such an instruction in `process` instead of retaining it, and log the drop
- [x] 1.4 Document why stripping is the safe direction, citing episode 194

## 2. Tell the composer

- [x] 2.1 Extend the delivery-direction guideline in `CORE_GUIDELINES` with what a cue may and may not ask for
- [x] 2.2 Name `deadpan` and `monotone` as directions to avoid

## 3. Tests

- [x] 3.1 A bare `[deadpan]` is dropped
- [x] 3.2 `[in a deadpan tone]` and `[flat and bored]` are dropped
- [x] 3.3 Episode 194's exact turn comes back with the cue gone and the words intact
- [x] 3.4 `[warm and conversational with an easy pace]` is still kept
- [x] 3.5 `[reset]`, `[sigh]` and `[laugh]` are unaffected
- [x] 3.6 A suppressed cue never reaches a chunk after re-emission
- [x] 3.7 Run `mvn test` and confirm the whole suite passes (1288 tests, 0 failures)

## 4. Repair episode 194

- [x] 4.1 Restart the app so the new rule is live
- [x] 4.2 Regenerate episode 194's audio and confirm the log shows the cue dropped (`Dropped delivery direction [deadpan]`)
- [x] 4.3 Confirm the new audio differs and the script is untouched (878s vs 876s; 13,114 vs 13,124 TTS chars, exactly the 10 characters of `[deadpan] `; script still 14,687 chars)
- [x] 4.4 Republish to SoundCloud and FTP (track 2392503327 -> 2392579263 on the same slug; FTP -> briefing-20260901-151759.mp3; feed follows)
