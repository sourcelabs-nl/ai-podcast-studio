# Design

## Context

See proposal.md. Replacing a SoundCloud track uploads the replacement, deletes the old track and then claims the canonical permalink. After the delete, the new track is the only live one.

## Goals / Non-Goals

**Goals:** the new track id is never lost after the old track is deleted; one way to answer a malformed request.

**Non-Goals:** the other findings of the closing review, which are in `code-review-backlog-2026-10-02.md`; a failure of the old-track delete after the upload, which keeps today's behavior (the old id stays on record and a retry is the documented path).

## Decisions

- **Permalink claim is best effort.** Its failure is logged and the freshly uploaded result, with the URL the upload landed on, is returned and persisted. Cancellation is rethrown. Alternative: fail and keep the old id, rejected because that id is dead.
- **Bad requests throw.** The controller sites throw `InvalidRequestException` with the same message, so `ApiExceptionHandler` answers the same 400 body. A `PageParams` helper holds the shared page and pageSize rule.
- **`ScriptJudge`** wraps the blocking attempt in `withContext(Dispatchers.IO)`, as every other LLM call site does, and rethrows cancellation ahead of the failure bookkeeping.

## Risks / Trade-offs

- [The persisted URL for a failed claim is the suffixed variant] -> The next republish or a playlist rebuild re-claims it; the link still plays.
