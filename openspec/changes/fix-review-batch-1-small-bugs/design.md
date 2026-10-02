# Design

## Context

See proposal.md for motivation. Three facts shape the approach:

- `PublishingService.unpublish` and the same-day replacement loop in `publish` both swallow a failed remote removal and then discard the record. The `episode-publishing` spec encoded that for the replacement path, so the spec changes with the code (see specs/episode-publishing/spec.md).
- `CachingChatModel` derives its key from the USER and SYSTEM messages only, and a lookup fires only when the prompt has no ASSISTANT or TOOL message. A tool loop's final turn does contain those messages, but its key is still readable by a later initial call, which is how the `llm-cache` spec requires tool-augmented compose calls to be cached.
- `RoleTagValidationAdvisor` retries by appending the rejected response as an ASSISTANT message followed by the correction as a USER message. That correction text is part of the key, so the stored row is never looked up again.

## Goals / Non-Goals

**Goals:**
- A failed remote removal never loses the identifier of the remote asset.
- Cancellation is never recorded as a failure.
- No unescaped request input reaches an HTML response.

**Non-Goals:**
- Treating a platform "not found" on unpublish as success. The user chose to keep the record on any failure, so an asset that is already gone leaves a record that needs manual attention.
- Merging the two OAuth controllers (a later batch); only the escaping is fixed here.

## Decisions

**Unpublish failure keeps the record.** Both paths record the failure in `errorMessage` and leave `status` and `externalId` alone. The replacement path still publishes the new episode, so a stale track can never block a publication; the manual path rethrows after saving the message so the endpoint reports the failure. Alternative: treat a 404 as success, rejected by the user in favour of the simpler rule.

**Side effects after a successful unpublish are guarded.** Playlist rebuild already was; `postPublish` and `export` now are too, in line with `runPostPublishSideEffects` used by `publish`, so the request outcome follows the removal and nothing else.

**Cancellation.** Each affected `catch (e: Exception)` gets a preceding `catch (e: CancellationException) { throw e }`. Where one block catches several times in a row, the rethrow is added to each. The helper type is `kotlinx.coroutines.CancellationException`, as `PublishingService` already imports. Alternative: a shared `runCatching`-style helper, rejected because it hides the control flow the rule is about.

**HTML escaping.** The shared `htmlPage` helper in each OAuth controller escapes the message with Spring's `HtmlUtils.htmlEscape`, so every caller is covered.

**Cache write for correction prompts.** A prompt is a role-tag correction when its last message is a USER message and it already contains an ASSISTANT message. The original prompt is everything before the first ASSISTANT message, so `CachingChatModel` can compute the original key itself and overwrite the rejected row (same id) with the retry's response. No hook between the advisor and the cache is needed. A tool loop's final turn ends in a TOOL message, so tool-augmented caching is unaffected. Alternative: have the advisor evict the rejected row through a callback, rejected as it couples the advisor to the cache for no gain.

## Risks / Trade-offs

- [A correction retry that is itself rejected leaves its response cached until the next retry overwrites it, and the last one stays cached when retries run out] -> A regenerate then retries again, which is what happens today.
- [A removal that can never succeed leaves a record that blocks nothing but stays PUBLISHED] -> The failure is visible in `errorMessage`, and the replacement path continues anyway.
- [Rethrowing cancellation changes the final state of an interrupted run] -> That is the intent: an interrupted run is left in its previous state instead of FAILED.
