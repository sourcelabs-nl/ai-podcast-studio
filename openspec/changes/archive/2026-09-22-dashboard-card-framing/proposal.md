## Why

The dashboard had grown three ways of framing the same thing. Some tables sat in a
bordered card and some sat loose on the page; some table headers carried a tint and
some did not; a section's heading sat above its card, so a tab that held one card
showed its own name twice, once in the tab trigger and once again underneath it.
None of that is visible while writing one tab, and all of it is visible when reading
the page.

Two tabs also asked the reader to do work the page could do. The evaluation tab
printed three scores as bare numbers, so whether an episode scored well took a
moment of arithmetic per row. The costs and latency tables were ordered by pipeline
stage, which is the order the code runs in and not the order anyone reads: the row
worth looking at is the dearest or the slowest one.

The dedup gate's row in the costs table named no model. The gate runs on a
configured model rather than one resolved per podcast, so the response had nowhere
to read a name from, and the row read as though the stage were free of a model
rather than as one whose model the API never sent.

## What Changes

**One frame, one place.** `Section` is removed. `Panel` takes an optional `title`
and renders it inside the border, so a card's heading belongs to the card. A tab
whose content is a single card passes no title, because the tab trigger already
names it. Table headers take their tint from the `Table` component rather than from
each call site, where six of thirteen had already been forgotten. The episodes and
sources tables move into a `Panel`, which they were missing.

**The Articles tab becomes a table.** It listed its sources as a stack of buttons
with chevrons, the one place in the app where a list of records was not a table. It
is now a table of sources, with the article cards in an expandable row, matching the
episodes list.

**Actions on the tab row.** The sources tab's Download and Add buttons render on the
same line as the tab triggers instead of above the table. The tab component portals
them into a node the page supplies, so placement stays the page's decision.

**Scores read at a glance.** Each attention score gets a filled bar and a colour
band derived from its own value. The bands say how high a number is, never whether
the episode passed: no threshold in `knowledge/evals/` is defensible yet, so the UI
shows magnitude and leaves the verdict to the reader.

**Ordered by what is being looked for.** The costs table sorts dearest first, the
latency stage table slowest first on p99, and the request list slowest first. TTS
characters move into the Input column instead of spanning both token columns, since
those columns hold whatever a stage is billed for.

**The gate names its model.** The episode costs response carries the configured
dedup gate model, for an episode whose gate actually issued requests.

**The layout decisions are written down.** `.claude/rules/frontend-layout.md` records
them, keyed to the frontend paths, so the next tab starts from them rather than from
whichever neighbouring file was opened first.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `cost-tracking`: the episode costs response gains the dedup gate's model, named
  only when the gate issued requests.
- `frontend-dashboard`: card framing, heading placement, tinted table headers, and
  the placement of a tab's own action buttons.
- `episode-detail-page`: the Articles tab as a table with expandable rows, the
  summary block's framing, the sources link in the subtitle, the evaluation tab's
  score bars, and the costs table's ordering and columns.
- `frontend-llm-latency`: the stage table and the request list are ordered slowest
  first.

## Impact

- `frontend/src/components/section.tsx`, `ui/table.tsx`, and every tab that framed
  content: `articles-tab`, `costs-tab`, `evaluation-tab`, `latency-tab`,
  `publications-tab`, `sources-tab`, `upcoming-costs-tab`.
- `frontend/src/app/podcasts/[podcastId]/page.tsx` and
  `frontend/src/app/podcasts/[podcastId]/episodes/[episodeId]/page.tsx`.
- `EpisodeController`, `PodcastMappers`, and `EpisodeCostsMapperTest`.
- `.claude/rules/frontend-layout.md` (new) and the rules list in `CLAUDE.md`.

No database change, no new endpoint, and no change to how any cost is computed.
Episodes generated before the gate's cost attribution landed still report a gate
row of zeros, and therefore no model.
