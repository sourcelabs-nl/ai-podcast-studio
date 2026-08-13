## Context

`generateBriefing` already creates the GENERATING episode upfront, emits SSE progress events, and marks the episode FAILED on error — so it is already safe to run as fire-and-forget. The only problem was the controller awaiting it inside a `suspend` request handler, which couples it to Spring MVC's async-request timeout. The scheduler (`BriefingGenerationScheduler`) and audio generation (`AudioGenerationService`) already use background `CoroutineScope(Dispatchers.IO + SupervisorJob())` launches; this change applies the same pattern to the manual endpoints.

## Goals / Non-Goals

**Goals:**
- Manual generate/regenerate cannot be cancelled by the HTTP request lifecycle.
- Return the new episode id immediately so the UI can navigate and follow SSE progress.

**Non-Goals:**
- Changing `/regenerate-recap` (short call, not at risk).
- Changing manual `PublishingController.publish` (separate latent risk, tracked separately).

## Decisions

**1. Background launch + 202, not a higher async timeout.** Returning 202 and launching on a managed scope fully decouples generation from the request; raising `spring.mvc.async.request-timeout` would only delay the problem and a client disconnect would still cancel the work.

**2. Create the GENERATING episode synchronously, run the pipeline in the launch.** The controller gets the episode id for its 202 response, while the multi-minute pipeline runs in `pipelineScope`. `generateBriefingAsync` returns null when an episode is already active so the controller can answer 409.

**3. Reuse the existing background-scope pattern.** `retryScope` is renamed `pipelineScope` and now hosts generate, regenerate, and retry launches — all long-running pipeline work that must outlive the request. Lifecycle unchanged (`@PreDestroy` cancels it).

**4. `updateLastGenerated` flag on `createGeneratingEpisode`.** Regeneration reuses the GENERATING-episode creation but must not bump `lastGeneratedAt`; the flag preserves the prior `updateLastGenerated = false` semantics of regeneration.

## Risks / Trade-offs

- **No-eligible-articles case**: the GENERATING episode is created before article eligibility is known; if none are eligible the background pipeline deletes it, so a UI that already navigated would see a not-found episode. Rare in practice (manual generate is triggered when articles are shown ready) → Mitigation: acceptable; the episode page handles missing episodes.
- **Controller tests** assert response shape → updated to expect 202/409.
