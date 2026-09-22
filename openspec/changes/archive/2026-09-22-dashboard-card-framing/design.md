## Context

See proposal.md - Why. What matters here is where the drift came from: the frame
was a convention rather than a component. `Section` rendered a heading and `Panel`
rendered a border, and nothing stopped a tab from using one, both or neither. Six
of thirteen tables had forgotten the header tint, two tables sat outside any panel,
and the Publications tab had grown a conditional wrapper to decide whether its
heading applied.

The dedup gate had a second, narrower problem: it is the only stage whose model is
configured globally instead of resolved per podcast, so the episode row has no
column for it and the mapper had nothing to read.

## Goals / Non-Goals

**Goals:**
- One component owns the frame, so a new tab cannot be framed a different way
  without deliberately writing different code.
- The decisions are recorded where the next change will meet them, not only in the
  components that happen to embody them.

**Non-Goals:**
- No change to how any cost, score or latency figure is computed. This is
  presentation, plus one field the API was not sending.
- No pass/fail gate on scores. That needs a defensible threshold, which
  `knowledge/evals/judged-baseline-2026-09.md` explicitly says the bundle does not
  yet have.
- No backfill of gate costs for existing episodes as part of this change.

## Decisions

**The heading moves into `Panel`, and `Section` is deleted rather than kept for
the cases that still want a heading above.** Keeping both would leave the same
choice open that caused the drift. `Panel` takes an optional `title`; a card with no
title is the normal case, since most tabs hold one card that their trigger already
names. The alternative, a `Card`/`CardHeader` pair in the shadcn idiom, was not
taken: it is more markup for a frame that only ever holds a heading and a body.

**The header tint moves into `TableHeader` itself.** The alternative was to add the
class at the six call sites that lacked it, which fixes today's instance of a
problem whose cause is that every call site can forget. A caller can still override
it through `className`, which is the escape hatch for a table that genuinely needs
to look different.

**A tab that owns action buttons portals them into a node the page gives it.** The
page renders an empty node beside the `TabsList` and passes it down; the tab renders
its buttons into it with `createPortal`. The alternative was lifting the handlers
(download, add) into the page, which would move the tab's own state and its dialogs
up with them. The node is held in state rather than in a ref, so the portal runs
once the node exists.

**Scores are coloured by fixed thirds of their own scale.** A band derived from the
archive distribution would read as a verdict against history, which is the claim the
knowledge bundle says cannot yet be made. Thirds of `[0, 1]` claim nothing beyond
the number itself.

**The gate's model is passed into the mapper from configuration, and suppressed
when the gate issued no requests.** The alternative was persisting the model on the
episode, which is the more correct answer and needs a migration plus a backfill; it
would say what each episode's gate actually ran on rather than what is configured
now. Suppressing the name for an episode with zero gate calls keeps the present
answer from being wrong in the one way that matters: it never names a model for an
episode that model never saw. If the configured gate model changes, an old
episode's row will name the new one, which is why this is the smaller answer and not
the final one.

**The Articles tab reuses the episodes list's expandable-row shape.** `Fragment` per
group, a row that toggles, and a second row with a `colSpan` cell holding the
detail. The article cards themselves are unchanged: only their container moved.

## Risks / Trade-offs

**The configured gate model can drift from what an episode ran on** → The name is
shown only for an episode whose gate ran, and the design note above records that
persisting it per episode is the correct fix when it matters.

**A colour band invites being read as a verdict anyway** → The bands sit on the
number's own scale with no threshold line, no badge and no wording, and the rule
file states the constraint so a later change does not quietly add one.

**A portalled button renders nothing until its tab has loaded its data** → The
sources tab returns early while loading, so its buttons appear with its table rather
than before it. This is a brief blank on the tab row, accepted over rendering
buttons that act on data that is not there yet.

## Migration Plan

None. No schema change, no API contract removal: `costs.dedupGate.model` was
already in the response shape and was always null.
