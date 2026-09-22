## ADDED Requirements

### Requirement: Content sits in a card, and the card carries its own heading
Every block of tabular or structured content in the dashboard SHALL be framed in a
card: a bordered, rounded container with consistent padding, drawn from one shared
component so the panels cannot drift apart.

A card's heading SHALL render inside that border, as the card's first line. No
heading shall sit above a card, because a heading outside the frame reads as
belonging to the page rather than to the content.

A card whose tab already names it SHALL carry no heading: repeating the tab
trigger's own words inside the card says nothing. A heading is for telling several
cards within one tab apart, or for naming a card something its tab does not.

An empty or failed state SHALL stay inside the card rather than replacing it, so a
labelled section that has nothing to show still says which section it is.

#### Scenario: A single-card tab shows no duplicate heading
- **WHEN** the user opens a tab whose content is one card, such as Articles, Publications or Costs
- **THEN** the card is shown with no heading repeating the tab's name

#### Scenario: A multi-card tab labels each card
- **WHEN** the user opens a tab holding several cards, such as Evaluation or Latency
- **THEN** each card carries its own heading, rendered inside its border

### Requirement: Table headers are tinted from the shared table component
A table's header row SHALL be tinted so it reads as distinct from the body, and that
tint SHALL come from the shared table component rather than from each place a table
is written. No table in the dashboard shall render an untinted header.

#### Scenario: Every table header is tinted
- **WHEN** any table in the dashboard renders
- **THEN** its header row carries the shared tint, without the calling code asking for it

### Requirement: A tab's own actions sit on the tab row
Buttons that act on a whole tab, such as adding or downloading its contents, SHALL
render on the same line as the tab triggers, aligned to the opposite end, rather
than above the tab's content. The page SHALL decide that placement, so a tab's
component cannot position its own buttons somewhere else.

#### Scenario: Sources actions share the tab row
- **WHEN** the user opens the Sources tab of a podcast
- **THEN** the download and add buttons appear on the same line as the Episodes, Publications and Sources triggers
