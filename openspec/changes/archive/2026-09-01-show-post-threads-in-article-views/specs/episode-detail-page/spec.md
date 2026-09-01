## MODIFIED Requirements

### Requirement: Article card display
Each article card SHALL display the article title, source label (or URL-derived fallback), relevance score with color coding, truncated summary (expandable), and a link to the original URL.

A single `ArticleCard` component SHALL serve both the episode Articles tab and the upcoming-articles page. The two previously kept near-identical copies that differed only in whether the subtopic badge was rendered; the shared card renders it in both.

When an article's `postCount` is greater than 1 the card SHALL show a post-count badge and SHALL be expandable to list the posts it was aggregated from, each with its timestamp and text, fetched from the article posts endpoint when the reader expands it. A card whose `postCount` is 1 SHALL show no badge and SHALL NOT be expandable, since its single post is the article itself.

Without this a fourteen-post conversation and a lone tweet render identically, which became the common case once short-form sources were aggregated into one article per author thread.

#### Scenario: Article card with full data
- **WHEN** an article has title, summary, relevance score, and URL
- **THEN** the card displays the title, a color-coded relevance score badge, truncated summary, and an external link icon/button

#### Scenario: Expand article summary
- **WHEN** user clicks on a truncated summary
- **THEN** the full summary text is revealed

#### Scenario: Source label fallback
- **WHEN** a source has no label set (null)
- **THEN** the display name is derived from the source URL (e.g., domain name or path)

#### Scenario: Thread size shown on an aggregated article
- **WHEN** an article has a `postCount` of 14
- **THEN** the card shows a badge reading "14 posts"

#### Scenario: Expanding a thread lists its posts
- **WHEN** the reader expands an article whose `postCount` is greater than 1
- **THEN** the posts are fetched from the article posts endpoint and listed oldest first, each with its timestamp and text

#### Scenario: Single-post article is not expandable
- **WHEN** an article has a `postCount` of 1
- **THEN** no post-count badge is shown and the card offers no thread expansion
