## 1. Sanitizer

- [x] 1.1 Create `TtsScriptSanitizer` object in `src/main/kotlin/com/aisummarypodcast/tts/TtsScriptSanitizer.kt` with `sanitize(script: String): String` that replaces em-dash and en-dash with `, ` and collapses any `,` immediately followed by a sentence terminator (`.`, `!`, `?`) back to the terminator alone
- [x] 1.2 Add `TtsScriptSanitizerTest` covering: em-dash inside sentence, en-dash inside sentence, multiple dashes, dash-before-terminator collapse, dash-free pass-through

## 2. Pipeline integration

- [x] 2.1 In `TtsPipeline.callProvider`, sanitize the script with `TtsScriptSanitizer.sanitize(...)` before constructing `TtsRequest`
- [x] 2.2 Verify (read-through) that the `Episode.scriptText` saved in `generate(...)` still uses the original `script` parameter, not the sanitized version

## 3. Prompt cleanup

- [x] 3.1 Remove em-dashes from prompt strings in `BriefingComposer.kt` (replace with commas, colons, or parentheses as appropriate)
- [x] 3.2 Remove em-dashes from prompt strings in `DialogueComposer.kt`
- [x] 3.3 Remove em-dashes from prompt strings in `InterviewComposer.kt`
- [x] 3.4 Update the sponsor-message template in `ComposerUtils.kt:68` to use a comma instead of an em-dash
- [x] 3.5 Add an explicit "do not use em-dashes / en-dashes" instruction via a shared `buildPunctuationBlock()` helper in `ComposerUtils.kt`, used by all three composer prompts

## 4. Verification

- [x] 4.1 Run `mvn test` and confirm all tests pass (770/770)
- [ ] 4.2 Restart the app (`./stop.sh && ./start.sh`) and regenerate one episode of the Agentic AI Podcast; confirm the generated MP3 no longer pronounces em-dashes
- [ ] 4.3 Run `/code-review --all` and address any violations introduced by the change
