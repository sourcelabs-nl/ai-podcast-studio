<!-- Implemented before this change was written; every task below is already done. -->

## 1. Drop rule

- [x] 1.1 Add `PACE_REDUCING_INSTRUCTION_WORDS` to `InworldScriptPostProcessor`, documenting the measured cost of `[measured and clear]` on an anchored turn
- [x] 1.2 Rename `flattensDelivery` to `degradesDelivery` and match against both word sets
- [x] 1.3 Widen the drop log message to name slowing as well as flattening and distortion

## 2. Composer guidance

- [x] 2.1 Drop the pace clause from the steering example, so it reads `[warm and conversational]`
- [x] 2.2 Reword the `CORE_GUIDELINES` steering bullet: a direction may adjust warmth, energy or brightness, never pace, and name `[measured]`, `[slowly]`, `[deliberate]`, `[unhurried]` among the forbidden cues

## 3. Tests

- [x] 3.1 `InworldScriptPostProcessorTest`: a pace-reducing cue is dropped by `process`
- [x] 3.2 `InworldScriptPostProcessorTest`: a cue that adds energy survives `process`
- [x] 3.3 `InworldScriptPostProcessorTest`: extend the whole-word test with pace-reducing and benign phrases
- [x] 3.4 Run `mvn test` and confirm the full suite passes (1417 tests, 0 failures)

## 4. Deploy

- [x] 4.1 Restart the application so subsequent generations use the new pipeline
