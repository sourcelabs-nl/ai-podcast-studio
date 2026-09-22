---
paths:
  - "frontend/src/app/**/*.tsx"
  - "frontend/src/components/**/*.tsx"
---

# Dashboard layout rules

How a page is framed. Component-level conventions (button variants, badge colours,
column order) live in `frontend/CLAUDE.md`; these are the rules about the frame
around them. Both apply.

**Cards.** Every block of tabular or structured content sits in a `Panel`
(`frontend/src/components/section.tsx`), never loose on the page. A tab that shows
one table shows it in a panel too.

**A card's heading sits inside the card.** `Panel` takes an optional `title` and
renders it as the first line within the border. There is no heading component that
sits above a panel, and an empty or failed state keeps its title by staying inside
the panel rather than falling outside it.

**No heading that says what the selected tab already says.** A tab whose content is
one card passes no `title`: Script, Articles, Publications and Costs are all named
by their tab trigger, and repeating that inside the card says nothing. A `title` is
for telling several cards within one tab apart (Latency, Evaluation), or for naming
a single card something the tab does not, the way the upcoming page's Costs tab
holds "Spent so far".

**Table headers are tinted.** `TableHeader` carries `bg-muted/50` from
`frontend/src/components/ui/table.tsx`. Never set it per call site, and never leave
a table without it.

**Tab actions sit on the tab row.** Buttons that act on a whole tab (add, download)
render on the same line as the `TabsList`, right-aligned, not above the content. A
tab component that owns such buttons takes the node to render them into and portals
them there, so the page decides the placement.

**Ordered by what the reader is looking for.** A cost table is sorted dearest first
and a latency table slowest first, not in pipeline order. A list whose point is to
find the outlier puts the outlier at the top.

**Scores are coloured by their own scale, never by a verdict.** A `[0, 1]` score
gets a filled bar and a colour band from its value (red below a third, amber below
two thirds, green above). Nothing in the app asserts that an episode passed or
failed an evaluation: no threshold in `knowledge/evals/` is defensible yet, so the
UI shows magnitude and leaves the judgement to the reader.

**Units belong in the column that holds them.** The cost table's Input and Output
columns hold whatever a stage is billed for: tokens for an LLM stage, characters
for TTS, a dash for a stage billed per call. Do not span a row across both columns
to fit another unit.
