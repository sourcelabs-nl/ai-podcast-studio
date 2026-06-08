## MODIFIED Requirements

### Requirement: Feed content:encoded shows topic-representative sources
Each episode item in the RSS feed SHALL include a `<content:encoded>` element with HTML content. The HTML SHALL contain:

1. The show notes (or recap, or sanitized script fallback) formatted as `<p>` paragraphs
2. A "Topics Covered" section listing the distinct topic names of the articles discussed in the episode as plain `<li>` items (no article titles, no links), in topic order
3. A sentence linking to the full sources page: "For the full list of sources that inspired this episode, [view all sources and show notes](url)."
4. A `<hr/>` separator followed by a contact footer: "Tips, comments, or feedback? Mail us at [email](mailto:email)" using the configured `ownerEmail`

Topic names SHALL be derived from the stored `topic` label of articles with a non-null `topicOrder` in the `episode_articles` table, deduplicated while preserving topic order. A blank (empty or whitespace-only) topic label SHALL be coalesced to "Other". The "Topics Covered" section SHALL be omitted entirely when no article with a non-null `topicOrder` has a non-blank topic label (e.g. pre-migration episodes, or episodes whose topic extraction stored blank labels); the sources-page link covers navigation to the full list.

When an episode has neither `showNotes` nor `recap`, the description SHALL fall back to a sanitized snippet of the script text: any leaked meta-commentary preamble SHALL be removed, speaker tags (e.g. `<interviewer>`, `<expert>`) SHALL be stripped while preserving the spoken text inside them, and the result SHALL be truncated. The same sanitized fallback SHALL be used for both the plain `<description>` and the HTML `content:encoded`. Raw speaker tags SHALL NOT appear in either element.

When no `ownerEmail` is configured, the contact footer SHALL be omitted.

#### Scenario: Content encoded with topic names only
- **WHEN** the feed is generated for an episode with 15 articles across 5 distinct topics
- **THEN** the `content:encoded` contains a "Topics Covered" section with the 5 topic names as plain list items, and no article titles or article links

#### Scenario: Content encoded with all-blank topic labels
- **WHEN** the feed is generated for an episode where every article has a non-null `topicOrder` but a blank topic label
- **THEN** the `content:encoded` omits the "Topics Covered" section and contains no empty list item

#### Scenario: Content encoded with mixed blank and named topics
- **WHEN** the feed is generated for an episode with one named topic and one blank-labeled topic, both with a `topicOrder`
- **THEN** the `content:encoded` "Topics Covered" section lists the named topic and an "Other" item for the blank label

#### Scenario: Content encoded with null topics (legacy episode)
- **WHEN** the feed is generated for an episode where all articles have `NULL` topic order values
- **THEN** the `content:encoded` contains the show notes, the full sources link, and the contact footer, but no topic or article list

#### Scenario: Description falls back to sanitized script when no show notes or recap
- **WHEN** the feed is generated for an episode with no `showNotes` and no `recap` whose script begins with a leaked preamble and contains `<interviewer>` / `<expert>` speaker tags
- **THEN** the plain `<description>` and HTML `content:encoded` contain the spoken text without any speaker tags or preamble

#### Scenario: Content encoded contact footer
- **WHEN** the feed is generated with `ownerEmail` configured as "podcast@example.com"
- **THEN** the `content:encoded` HTML ends with a `mailto:` link to "podcast@example.com"

#### Scenario: Content encoded without ownerEmail
- **WHEN** the feed is generated without `ownerEmail` configured
- **THEN** the `content:encoded` HTML omits the contact footer
