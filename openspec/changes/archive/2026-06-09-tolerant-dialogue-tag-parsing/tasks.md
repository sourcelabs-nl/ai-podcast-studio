# Tasks

- [x] Rewrite `DialogueScriptParser.parse` to tokenize on any opening/closing tag, deriving the turn role from the opening tag and ending the turn at the next tag token.
- [x] Preserve existing behavior: ignore (with warning) text that appears outside any opening tag; recover an unterminated final turn.
- [x] Add tests for mismatched closing tags, missing closing tags, consecutive same-speaker turns, and an unterminated final turn.
- [x] Mirror the tolerant logic in the frontend `parseMultiSpeakerScript` (`script-viewer.tsx`) and confirm `tsc --noEmit` passes.
- [x] Repair the one affected episode (141): normalize stored `script_text` to matching tags (38 turns) with a backup, then regenerate its audio.
- [x] Run `mvn test` (full suite green).
