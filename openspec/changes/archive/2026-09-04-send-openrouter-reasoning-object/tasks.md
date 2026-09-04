## 1. Send the form OpenRouter reads

- [x] 1.1 Add the effort to `OpenRouterRouting.extraBodyFor` as `reasoning: {effort, exclude}`
- [x] 1.2 Stop sending the flat `reasoning_effort` to OpenRouter, and record why sending both is worse than useless under `require_parameters`
- [x] 1.3 Keep the flat field for a direct `openai` model, where it is the real one
- [x] 1.4 Send no `reasoning` block for an effort of `none`, and record the routing risk that motivates it
- [x] 1.5 Rename the helper to `withRoutingAndReasoning` and apply it in compose, scoring, dedup and recap
- [x] 1.6 Record that `message.reasoning` has no field in `openai-java`, so the text was never observable

## 2. Tests

- [x] 2.1 An OpenRouter compose request carries the effort in the `reasoning` block and no flat field
- [x] 2.2 A per-podcast override and a blank override both land in the block
- [x] 2.3 An effort of `none` sends no `reasoning` block but keeps the provider floor
- [x] 2.4 The block excludes the reasoning text from the response
- [x] 2.5 A direct `openai` model still carries the flat field and no extra body
- [x] 2.6 Run `mvn test`

## 3. Verify against the real pipeline

- [ ] 3.1 Run a generation and confirm compose output tokens now exceed the script length, which is what reasoning actually taking effect looks like
