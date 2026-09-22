## ADDED Requirements

### Requirement: Attention scores are shown as magnitude, not as a verdict
Each attention score on the Evaluation tab SHALL be shown as a filled bar alongside
its number, for the overall score and for each of its components, so the height of a
score is readable before its digits are.

The colour of a bar and its number SHALL be derived from the score's own position on
its `[0, 1]` scale. The presentation SHALL NOT state or imply that an episode passed
or failed an evaluation: no scoring threshold is yet defensible, so the reader is
shown how high a number is and left to judge it.

#### Scenario: A score is readable without arithmetic
- **WHEN** the user opens the Evaluation tab for a scored episode
- **THEN** the overall score and each component are shown with a filled bar whose length and colour follow the value

#### Scenario: No pass or fail is asserted
- **WHEN** any score is displayed
- **THEN** no label, badge or wording states that the episode met or failed an evaluation threshold

### Requirement: The costs table is ordered by spend
The per-stage costs table SHALL be ordered by cost, dearest stage first, rather than
in pipeline order. The stage worth examining is the one the episode paid most for.

#### Scenario: The dearest stage is first
- **WHEN** the user opens the Costs tab for an episode whose TTS cost exceeds every LLM stage
- **THEN** the TTS row appears above the LLM stage rows, and the Total footer still sums every row

### Requirement: A cost row's Input and Output columns hold what its stage is billed for
The Input and Output columns SHALL hold whatever quantity a stage is charged on:
token counts for an LLM stage, character counts for TTS, and a dash for a stage
billed per call. A row SHALL NOT span those two columns to accommodate another unit.

#### Scenario: TTS characters sit in the Input column
- **WHEN** the Costs tab renders a row for TTS
- **THEN** its character count appears in the Input column and its Output column shows a dash

### Requirement: The episode summary is framed like the upcoming-episode banner
The show-notes summary above the tabs SHALL use the same framing as the podcast
page's upcoming-episode banner: the same border, rounding, tint and padding, so two
blocks that play the same role on two pages do not read as different components.

#### Scenario: Summary matches the banner
- **WHEN** an episode with show notes is opened
- **THEN** its summary block is framed identically to the upcoming-episode banner on the podcast page

### Requirement: The subtitle links to the published sources page
When an episode has been published to a target that also publishes a sources page,
the episode subtitle SHALL carry a link to that page, opening in a new tab. An
episode that has not been published SHALL show no such link, since the page does not
exist.

#### Scenario: A published episode links to its sources page
- **WHEN** an episode has been published and its audio is reachable at a public URL
- **THEN** the subtitle carries a link to the sources page published alongside that audio

#### Scenario: An unpublished episode carries no link
- **WHEN** an episode has not been published anywhere
- **THEN** the subtitle carries no sources link

### Requirement: The Articles tab is a table of sources with expandable rows
The Articles tab SHALL list the episode's sources as table rows, one per source,
carrying the source's name, its type, how many articles the episode drew from it and
the best relevance score among them. Rows SHALL be ordered by article count,
descending.

A row SHALL expand in place to show that source's article cards, and SHALL start
collapsed, because an episode links dozens of articles across many sources and the
tab is read first as a list of sources. The whole row SHALL toggle, not only the
chevron marking its state.

#### Scenario: Sources are listed as rows
- **WHEN** the user opens the Articles tab
- **THEN** each source appears as one table row with its name, type, article count and top relevance score, the source contributing most articles first

#### Scenario: A row expands to its articles
- **WHEN** the user clicks anywhere on a source's row
- **THEN** that source's article cards appear directly beneath it, and clicking again collapses them
