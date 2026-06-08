## Why

A review of a recently generated episode (after the audio-readability improvements) surfaced three remaining patterns that hurt listenability: raw social-media handles read aloud (e.g. "/trq212/ on X", "/hwchase17/"), hyphenated/dotted package and domain names voiced character-by-character (e.g. "datasette-agent-micropython", "warp.dev"), and a fatiguing firehose of research-paper codenames stacked with author surnames ("X from Smith and colleagues"). These are addressed at the compose-prompt layer, the same lever as the existing audio rules.

## What Changes

- Extend `buildModelNamesBlock()` to cover software package, repository, and domain identifiers in addition to model/product names, replacing hyphens, slashes, and dots with natural spoken words. Label changes from "MODEL & PRODUCT NAMES" to "MODEL, PRODUCT & PACKAGE NAMES".
- Add `buildHandlesBlock()` ("SOURCE NAMES, NOT HANDLES"): never read social-media usernames/handles aloud; use real names or generic descriptors, and never wrap handles in IPA slash notation.
- Add `buildResearchNamesBlock()` ("RESEARCH NAMES FOR THE EAR"): lead with what a piece of research does, voice codenames only when the name is the news, and ration author-surname attributions.
- Wire all three (the extended model block plus the two new blocks) into `BriefingComposer`, `DialogueComposer`, and `InterviewComposer` alongside the existing audio rules.
- Applies to scripts composed after the change; already-generated episodes are unaffected.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `llm-processing`: The compose-stage prompt's spoken-names rule is broadened to packages/repos/domains, and two new shared rules (handles, research names) are added across all three composers.

## Impact

- Code: `ComposerUtils.kt` (extend one function, add two), `BriefingComposer.kt`, `DialogueComposer.kt`, `InterviewComposer.kt` (wire the blocks).
- Tests: composer prompt tests assert each rule is present; existing model-names assertions updated to the new label.
- No API, schema, or dependency changes. No breaking changes.
