## Why

Thread detection knew that a post was a reply and threw away who it replied to. `isReply` tested the `R to @` title prefix only, and the reply was then appended to the author's most recent thread whatever account it answered. A reply the author addressed to somebody else therefore landed in the middle of an unrelated conversation of theirs, and the two were composed into one article.

Reading one Narro feed live settles both how often that happens and whether anything better is available. Of 50 items, 21 were root posts, 28 were replies naming the same account as the author, and 1 was a reply naming a different account. So the defect affects roughly 2% of items while the self-threads it must not disturb are 28 of the 29 replies.

Nothing stronger than the account name is on offer. The feed carries `title`, `link`, `guid`, `pubDate`, `dc:creator`, `description` and `enclosure`; `guid` is a per-item UUID, `link` is the post's own status URL, and there is no `conversation_id` and no `in_reply_to_status_id` anywhere in the document. The reply marker names the account replied to, never the tweet. Threading by heuristic is what the feed allows, not a shortcut taken here. Recorded in `knowledge/references/narro-feed-format.md`.

## What Changes

- A reply continues the preceding thread only when the account it answers is the author of the group. A reply to any other account starts its own thread.
- A group with no resolvable author key keeps the older, author-blind attachment, since there is nothing to compare the target against and the grouping that feeds like that get today still works.
- The author key drops a leading `@`, because feeds write the author field both as `@simonw` and as `simonw` and a reply target must compare equal either way.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `source-aggregation`: reply attachment is conditioned on the reply target matching the author.

## Impact

- Backend: `SourceAggregator` (`groupPostsByThread` takes the group's author key, `isReply` becomes `continuesThread`, `resolveAuthorKey` strips a leading `@`).
- Tests: `SourceAggregatorTest` gains 2 cases; existing cases that used a reply target inconsistent with the post's own author were corrected to name it.
- Knowledge: `knowledge/references/narro-feed-format.md` records what the feed carries and the sampled distribution the rule rests on.
- No schema, API, frontend, or configuration change.
- One case remains indistinguishable in this feed's data: two self-threads by the same author interleaved within one window. Nothing in the document separates them.
