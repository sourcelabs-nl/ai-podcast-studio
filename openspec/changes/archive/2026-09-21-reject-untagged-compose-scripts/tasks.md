> Retrofitted change: the implementation landed before this change was written, so every task is
> already complete. Recorded here so the change carries the same task breakdown as any other.

## 1. Validation

- [x] 1.1 Replace `RoleTagValidationAdvisor`'s invalid-role set with a private `TagProblem(summary, correction)` returned by a `validate()` function, and drive the existing retry loop, log line, and thrown exception from it
- [x] 1.2 Add the missing-tag branch to `validate()`, with a correction telling the model every line of spoken text must sit inside a tag and naming the allowed tags
- [x] 1.3 Report an invalid tag ahead of a missing one, so a response carrying only `<function_results>` keeps its existing, more specific error
- [x] 1.4 Add `hasSpeakerTag()`, matching an opening tag for any allowed role in either `<role>` or `[role]` form and not requiring a matching closer
- [x] 1.5 Update the class doc-comment to describe both failure modes and note that the advisor is registered only where speaker tags are mandatory

## 2. Prompt

- [x] 2.1 Add the mandatory-speaker-tags rule to `ComposerUtils.buildSpeakerTagFormatBlock`, ahead of the existing delimiter rule, so both the dialogue and interview prompts carry identical wording
- [x] 2.2 Update the block's doc-comment to cover both ways the tags go wrong

## 3. Tests

- [x] 3.1 Add a test that an untagged script retries and self-corrects, using episode 187's actual opening text, asserting the retry prompt names the failure and the allowed roles
- [x] 3.2 Add a test that a square-bracketed opener is accepted without retrying
- [x] 3.3 Add a test that exhausting the retry budget on an untagged script throws an error naming the missing tags
- [x] 3.4 Confirm the existing invalid-tag tests still pass unchanged, verifying the ordering decision preserved that path

## 4. Verification

- [x] 4.1 Run `mvn test` and confirm the full suite passes
- [x] 4.2 Restart the application on the new build
- [x] 4.3 Regenerate the failed episode and confirm the resulting script carries matched speaker tags through to published audio
