## Context

Inworld's [Generating Naturally Sounding Speech](https://docs.inworld.ai/tts/best-practices/generating-speech) guide describes the levers that actually move perceived quality on their models. Our integration predates most of it. Three things are simply wrong: `deliveryMode: "EXPRESSIVE"` is not a value the enum has (`STABLE | BALANCED | CREATIVE`), `[clear_throat]` is not a sound name (it is `[clear throat]`, and an unrecognised name is reinterpreted as a steering instruction), and `DEFAULT_MODEL` names `inworld-tts-1.5-max` while every configured podcast runs `inworld-tts-2`.

Beyond the bugs, we synthesize each chunk as an isolated request. The model therefore has no idea that chunk 4 continues the sentence energy of chunk 3, which is what makes splice points audible and short dialogue turns land flat. Inworld's answer is `synthesisContext.previousRequests`, and it costs us nothing structurally: we chunk the whole script up front, so every chunk's predecessors are already known and generation stays fully parallel.

## Goals / Non-Goals

**Goals:**
- Stop sending values the API does not recognise (`EXPRESSIVE`, `[clear_throat]`).
- Give each synthesis request the conversational context that precedes it.
- Let the composer direct delivery through steering instructions, and keep that direction alive across chunk boundaries.
- Split at places a listener would pause anyway, with headroom below the hard 2000-character limit.
- Send the request fields Inworld documents as quality levers: `language`, a `speakingRate` inside the supported range.

**Non-Goals:**
- Streaming synthesis. We stay on the non-streaming `/tts/v1/voice` endpoint.
- The request-level `instruction` field. Steering is expressed in the script text so the composer owns it, and one mechanism is easier to reason about than two.
- Voice cloning, markup for languages other than the podcast's configured one.
- Re-generating existing episodes. Only new audio is affected; the data migration only rewrites the stored setting.

## Decisions

**`CREATIVE` replaces `EXPRESSIVE` everywhere, with a one-off data migration.**
`EXPRESSIVE` was never a documented value. The enum is `DELIVERY_MODE_UNSPECIFIED | STABLE | BALANCED | CREATIVE`, and `CREATIVE` is the end of the scale `EXPRESSIVE` was reaching for, so it is a rename rather than a semantic change. Stored `ttsSettings` are a JSON text column, so `V65` rewrites the value in place with `REPLACE` rather than parsing JSON in SQL. Podcasts that never set the key are untouched. The alternative, treating `EXPRESSIVE` as an alias in the provider, was rejected: it keeps an invented value alive in the UI and in stored settings forever.

**`DEFAULT_MODEL` becomes `inworld-tts-2`.**
This is the model in use, the only one that supports steering, and the one the default should describe. TTS cost is estimated from `TtsResult.model` (the model actually used), not from a default constant, so the pricing already tracked reality and does not shift.

**Steering lives in the script, and the provider re-emits it per chunk.**
An instruction stays in force until it is changed or `[reset]`, but "in force" means within one request. Our chunker splits a turn into several requests, so without intervention the direction silently expires at the first splice. `InworldSteering.reemitInstructions` walks the chunks in order, tracks the active instruction, and prepends it to any chunk that does not already open with one. Tracking is reset per dialogue turn, because a direction given to the host should not carry into the co-host's voice.

**Bracketed-tag handling becomes model-aware rather than accidental.**
Today `BRACKETED_TAGS = \[(\w+)]` strips `[excited]` but lets `[say excitedly]` through, purely because `\w+` does not match a space. That is not a policy. The new rule: sound names in the whitelist are always kept (and `[clear_throat]` is normalised to `[clear throat]`); any other alphabetic tag is a steering instruction, kept on models that support steering and stripped on models that would read it aloud. Non-alphabetic tags such as `[1]` are always stripped, so stray citation markers do not become instructions.

**The script guidelines describe steering unconditionally.**
`TtsProvider.scriptGuidelines(style, pronunciations)` has no model parameter, and threading one through the interface for a single provider would be a wide change for a narrow need. Since the post-processor strips steering tags on models that cannot use them, an instruction emitted by the composer is harmless on `inworld-tts-1.5-*`: it simply disappears before the request is built. The default model supports steering, so the common path uses them.

**Context window: at most 3 preceding texts and 2000 characters, whichever binds first.**
Inworld does not document a limit, and an unbounded window would grow the request linearly with script length. Three preceding chunks is enough for prosodic continuity across a splice; the character cap keeps a single long predecessor from dominating. Whether context text counts toward `usage.processedCharactersCount` (and therefore billing) is unverified, which is another reason to keep the window small.

**Chunk at 1900, paragraph first.**
The API limit is 2000. Chunking exactly at the limit leaves no room for the steering instruction the provider prepends, so 1900 gives that headroom. `TextChunker` now tries separators in order (blank line, newline, sentence end, space) and only falls back to a hard cut when a single unbreakable run exceeds the maximum, matching Inworld's reference chunker. Separators are kept attached to the text that precedes them, so paragraph structure survives the round trip instead of being flattened to single spaces.

**`speakingRate` is clamped in the API client, not in the provider.**
`[0.5, 1.5]` is a property of the Inworld request, and `buildSynthesisBody` is the one place that writes the field. Clamping there means no caller can get a rejected request past it. Values below the recommended `0.8` are warned about but honoured, because they are legal and someone may want them.

**`language` is the podcast's ISO 639-1 code, sent as-is.**
A two-letter ISO 639-1 code is already a well-formed BCP-47 language tag, and Inworld's supported set is expressed in exactly those codes (`en`, `nl`, `de`, ...). Inventing a region subtag (`en` to `en-US`) would be a guess about accent that the podcast owner never made.

## Risks / Trade-offs

- **`synthesisContext` may be billed** → Unverified against the live API. The window is capped at 3 texts / 2000 characters, so the worst case is a bounded multiple of the current character count, and the setting is a constant that can be dropped to zero.
- **`CREATIVE` acceptance is unverified against the live API** → Documented, but not probed. If rejected, the request fails loudly with the API's error body rather than degrading silently.
- **Steering instructions are LLM-authored free text** → A bad instruction changes delivery for a whole chunk. Mitigated by the guidelines asking for at most one per turn and by `[reset]`, and bounded by the fact that a chunk is at most 1900 characters.
- **Re-emitted instructions consume characters in every chunk** → An instruction is tens of characters against a 1900-character chunk; the alternative is losing the direction entirely after the first splice.
- **Chunk boundaries move** → Paragraph-first splitting produces different, generally fewer and more natural, splice points than sentence-first. Existing episodes are unaffected; regenerated audio will differ slightly from its previous rendering.
