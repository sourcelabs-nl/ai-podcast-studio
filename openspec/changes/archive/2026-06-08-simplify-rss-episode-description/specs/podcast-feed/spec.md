## MODIFIED Requirements

### Requirement: Feed content:encoded shows topic-representative sources
Each episode item in the RSS feed SHALL include a `<content:encoded>` element with HTML content. The HTML SHALL contain:

1. The show notes (or recap, or script fallback) formatted as `<p>` paragraphs
2. A "Topics Covered" section listing the distinct topic names of the articles discussed in the episode as plain `<li>` items (no article titles, no links), in topic order
3. A sentence linking to the full sources page: "For the full list of sources that inspired this episode, [view all sources and show notes](url)."
4. A `<hr/>` separator followed by a contact footer: "Tips, comments, or feedback? Mail us at [email](mailto:email)" using the configured `ownerEmail`

Topic names SHALL be derived from the stored `topic` label of articles with a non-null `topicOrder` in the `episode_articles` table, deduplicated while preserving topic order. Articles with a `NULL` topic but a non-null `topicOrder` SHALL be labeled "Other".

When an episode has no topic data (e.g., pre-migration episodes with `NULL` topic order values), the `content:encoded` SHALL NOT include an inline article or topic list; the sources-page link covers navigation to the full list.

When no `ownerEmail` is configured, the contact footer SHALL be omitted.

#### Scenario: Content encoded with topic names only
- **WHEN** the feed is generated for an episode with 15 articles across 5 distinct topics
- **THEN** the `content:encoded` contains a "Topics Covered" section with the 5 topic names as plain list items, and no article titles or article links

#### Scenario: Content encoded with null topics (legacy episode)
- **WHEN** the feed is generated for an episode where all articles have `NULL` topic order values
- **THEN** the `content:encoded` contains the show notes, the full sources link, and the contact footer, but no topic or article list

#### Scenario: Content encoded with no articles
- **WHEN** the feed is generated for an episode with no linked articles
- **THEN** the `content:encoded` contains the show notes, the full sources link, and the contact footer, but no topic list

#### Scenario: Content encoded contact footer
- **WHEN** the feed is generated with `ownerEmail` configured as "podcast@example.com"
- **THEN** the `content:encoded` HTML ends with a `mailto:` link to "podcast@example.com"

#### Scenario: Content encoded without ownerEmail
- **WHEN** the feed is generated without `ownerEmail` configured
- **THEN** the `content:encoded` HTML omits the contact footer
