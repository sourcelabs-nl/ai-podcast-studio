## Why

The Articles tab opens with every source group expanded. A daily episode links dozens of articles across a dozen sources (episode 181: 38 articles in 13 groups), so the tab opens as a wall of cards: measured, the page is roughly 3000 pixels tall with a single group expanded and far longer with all of them. Finding which sources contributed means scrolling past everything.

## What Changes

- Source groups in the Articles tab start collapsed. The tab opens as a scannable list of source names with their article counts, and the reader expands the groups they care about.
- Collapsing and expanding is otherwise unchanged, as is the per-article summary truncation inside a card.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `episode-detail-page`: the Articles tab's grouped display gains a defined default state, collapsed.

## Impact

- Frontend: `frontend/src/components/articles-tab.tsx`. The state it tracks flips from "which groups are collapsed" to "which groups are expanded", so an empty initial set now means everything is collapsed.
- No backend, API, or database change.
