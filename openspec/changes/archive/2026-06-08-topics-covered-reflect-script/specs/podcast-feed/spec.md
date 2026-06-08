## MODIFIED Requirements

### Requirement: Feed content:encoded shows topic-representative sources
Each episode item in the RSS feed SHALL include a `<content:encoded>` element with HTML content. The HTML SHALL contain:

1. The show notes (or recap, or sanitized script fallback) formatted as `<p>` paragraphs
2. A "Topics Covered" section listing the distinct topic names of the articles **actually discussed in the episode script** as plain `<li>` items (no article titles, no links), in topic order
3. A sentence linking to the full sources page
4. A `<hr/>` separator followed by a contact footer using the configured `ownerEmail`

A topic SHALL be considered discussed only when at least one of its linked articles has a non-null `topicOrder`. `topicOrder` SHALL be retained only for articles whose topic was identified as discussed in the script (see the episode-show-notes capability); articles whose topic was not discussed SHALL have `topicOrder` set to `NULL` and SHALL NOT contribute to "Topics Covered". Blank topic labels are coalesced to "Other", and the section is omitted entirely when no discussed, non-blank topic remains.

#### Scenario: Topics Covered reflects only discussed topics
- **WHEN** an episode links 57 articles spanning 42 candidate topics but the script discusses only 15 of them
- **THEN** the `content:encoded` "Topics Covered" section lists only those 15 discussed topics, not all 42

#### Scenario: Non-discussed articles remain available via the sources page
- **WHEN** an article's topic was not discussed in the script
- **THEN** it does not appear under "Topics Covered" in the feed, but remains linked and listed on the episode sources page
