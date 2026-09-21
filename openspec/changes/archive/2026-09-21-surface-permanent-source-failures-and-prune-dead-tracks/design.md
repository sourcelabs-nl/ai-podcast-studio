## Context

See proposal.md (Why). Both fixes are small and local; what matters is the reasoning behind the
defaults they change.

## Goals / Non-Goals

**Goals:**

- A refused feed becomes visible in the source's recorded state instead of retrying quietly.
- A rebuild stops asking SoundCloud about tracks that are provably gone.

**Non-Goals:**

- Notifying the user actively (email, push). Recording the state correctly is a precondition for
  that, not a substitute; the alerting surface is a separate piece of work.
- Preventing quota freeing from orphaning publication rows in the first place. Correcting on the
  observed 404 covers tracks deleted by quota freeing, by a republish, and by hand in SoundCloud's
  own UI, at a cost of one wasted request per track, once.

## Decisions

**Permanent is the default for 4xx, transient the exception.** The failing classification was a
list of statuses someone had thought of. Inverting it means a status nobody anticipated errs
towards being surfaced rather than being swallowed, which is the safer direction: a permanent
classification on a genuinely transient status costs a source some polls and shows a reason in the
UI, while the reverse costs the podcast its content with no signal at all. Only 408 and 429 stay
transient, because those two statuses are the protocol's own way of saying "retry later".

**A 404 is treated as proof, and the row is corrected to match.** The alternative, keeping a
separate "known dead" list, adds state that has to be maintained and can itself go stale. Setting
the publication to `UNPUBLISHED` with a cleared `externalId` uses the shape the unpublish flow
already produces, so the dashboard, the republish path and the playlist query all behave correctly
with no further changes. It also makes the correction self-healing for the 101 rows already wrong.

**The correction is not transactional.** Its caller makes SoundCloud HTTP calls, and this codebase
has already been bitten by transactions held across slow external I/O. Each row is an independent,
idempotent correction, so a partial failure simply leaves the rest for the next rebuild.

## Risks / Trade-offs

- **A sustained but genuinely temporary 4xx can now auto-disable a source.** → The threshold is 15
  consecutive failures (about 7 hours at a 30-minute interval), and re-enabling resets the failure
  state. The recorded `disabledReason` names the status.
- **Episodes whose track was deleted for quota now display as not published on SoundCloud.** → That
  is what is true. It also makes the quota-freeing side effect visible, which it was not before.
- **A transient SoundCloud 404 (an outage answering wrongly) would unpublish a live track's row.** →
  The track id is only cleared, never deleted remotely, so a republish restores it. SoundCloud
  returning 404 for a live track has not been observed in eight days of logs.

## Migration Plan

No migration. The 101 already-wrong rows correct themselves on the next playlist rebuild. Rollback
is reverting the code; rows already corrected stay corrected, which is harmless because they
describe reality.
