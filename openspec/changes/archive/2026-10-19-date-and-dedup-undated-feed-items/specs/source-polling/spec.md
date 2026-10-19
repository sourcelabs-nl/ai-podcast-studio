## ADDED Requirements

### Requirement: Undated items are dated when first seen

A polled item that carries no publication date SHALL be stored with the moment it was first seen
as its `published_at`, the latest it can have been published. Existing undated posts SHALL take
their `created_at`, and existing undated articles the earliest `created_at` of their posts; an
article with no post keeps no date.

#### Scenario: An undated feed item
- **WHEN** a feed item without a date is polled
- **THEN** the stored post's `published_at` equals its `created_at`

#### Scenario: A dated feed item
- **WHEN** a feed item with a date is polled
- **THEN** the stored post keeps that date

### Requirement: An item is stored once per URL

A polled item SHALL be skipped when the source already has a post with the same URL, even when the
item's content differs. A website source SHALL be exempt, because its one URL is watched for its
content changing.

#### Scenario: A feed re-serves an item with edited text
- **WHEN** an RSS item arrives with a URL the source already has, and a different body
- **THEN** no new post is stored

#### Scenario: A website page changes
- **WHEN** a website source's page content changes under its unchanged URL
- **THEN** the new content is stored
