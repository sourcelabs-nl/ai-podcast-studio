# Proposal

## Why

An undated feed item is admitted to every episode window, and a feed that re-serves an item with edited text creates a new article each time. Episode 297 (window 16-19 October) carried eight undated Google Developers Blog articles first seen between 24 September and 19 October 16:16Z (after the window ended), three of them the same URL three times.

## What Changes

- A polled item without a publication date takes the moment it was first seen as its `published_at`.
- An item whose URL the source already has a post for is skipped, even when its content changed. Website sources are exempt: their single URL is watched for content changes.
- Migration V88 backfills `published_at` for existing undated posts (their `created_at`) and articles (the earliest `created_at` of their posts), and indexes `posts(source_id, url)`.

## Capabilities

### New Capabilities

### Modified Capabilities
- `source-polling`: adds "Undated items are dated when first seen" and "An item is stored once per URL".

## Impact

- `SourcePoller`, `PostRepository.existsBySourceIdAndUrl`, migration `V88__date_undated_posts_and_index_post_url.sql`. Existing duplicate articles are left as they are.
