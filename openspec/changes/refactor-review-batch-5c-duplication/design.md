# Design

## Context

See proposal.md. The briefing composer has a different shape (no speaker tags, no tag validation), so it is not part of the shared skeleton. The prompts of the two tagged composers differ in substance and stay in their own classes.

## Goals / Non-Goals

**Goals:** one implementation of the tagged compose call; one thread-safe way to send SSE events; `publish` readable as a sequence of named steps.

**Non-Goals:** merging the prompt builders, touching `BriefingComposer`, changing any prompt text (the LLM cache key hashes it), or changing publication behavior.

## Decisions

- **`TaggedScriptComposer` takes a request object.** A `TaggedComposeRequest(kind, roles, articles, podcast, model, context, prompt)` carries the six inputs, since the signature would otherwise pass the parameter-object threshold. `kind` names the script ("dialogue", "interview") for the log lines and the empty-response error, which keep their current wording. It owns the chat client, the tag validation advisor, the usage fold, the clean-up and the provenance.
- **Composers keep what is theirs.** Each builds its prompt and passes its roles (the interview's fixed set, the dialogue's `resolveSpeakerRoles(podcast)`, computed once instead of twice) and keeps the 3-argument `compose` overload that resolves the model.
- **`SafeSseEmitter` wraps an `SseEmitter`** and takes one lock for `send`, `complete` and `completeWithError`. The audio endpoint's `sendLock` is replaced by it. Alternative: a synchronized block at each call site, rejected because that is the drift this fixes.
- **`PreviewStreamer` owns the preview scope and the stream.** It starts the pipeline preview on `Dispatchers.IO`, runs the heartbeat, forwards progress, sends the result or the error event and completes, with the same event names and payloads as today. The controller keeps the lookups and returns the emitter.
- **`publish` steps.** Validation of the request, the lookup of an existing live publication, same-day replacement, the claim, the upload with its failure handling, and the success bookkeeping become private functions in the same order. The catch structure around the upload is unchanged.

## Risks / Trade-offs

- [A refactor of the composers changes the prompt or the options] -> The existing composer tests assert the prompt and the call options; they must pass untouched except for construction.
- [A lock around `complete` blocks a sender] -> Sends are short writes to the servlet response, and the lock is per emitter.
