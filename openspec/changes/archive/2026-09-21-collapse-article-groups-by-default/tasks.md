<!-- Implemented before this change was written; every task below is already done. -->

## 1. Articles tab

- [x] 1.1 Flip the tracked state in `articles-tab.tsx` from `collapsedGroups` to `expandedGroups`, so an empty initial set collapses every group
- [x] 1.2 Invert the chevron and the article-card render condition to follow the expanded flag

## 2. Verification

- [x] 2.1 Open the Articles tab for an episode with many sources and confirm every group is collapsed, with counts visible
- [x] 2.2 Confirm expanding one group reveals only its articles and collapsing it again hides them
