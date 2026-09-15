---
okf_version: "0.2"
type: reference
title: What a Narro RSS feed carries, and what it does not
status: stable
method: >
  Fetched one combined feed live and read all 50 items: the element names present
  on each item, the namespaces declared, the uniqueness of every guid, the host
  and shape of every link, and the class names used inside the description HTML.
stale_after: 2027-09-15T00:00:00Z
generated:
  by: claude-opus-5
  at: 2026-09-15T00:00:00Z
---

Narro merges many X accounts into one RSS document, which is why threading is
scoped per author before it is scoped per conversation.

**The fields.** An item carries `title`, `link`, `guid`, `pubDate`,
`dc:creator`, `description` and `enclosure`, and `dc` is the only namespace
declared. `guid` is a Narro-internal UUID, distinct on all 50 items, so it
identifies the item and never the conversation. `link` is the post's own status
URL (`x.com/<handle>/status/<id>`), never the parent's, which is what makes the
posting handle recoverable from the URL even when `dc:creator` holds a display
name.

**There is no conversation id.** No `conversation_id` and no
`in_reply_to_status_id` appear anywhere in the document. A thread therefore
cannot be reconstructed from an identifier and has to be inferred, which is a
property of the feed rather than a shortcut taken here.

**What marks a reply.** The `description` HTML uses `narro-reply-header` and
`narro-reply-text`, `narro-quote-header` and `narro-quote-text`,
`narro-rt-header` and `narro-rt-text`. The reply header names the **account**
replied to, never the tweet, so the strongest available signal is whether a
reply answers its own author. Quotes and retweets are deliberately not treated
as replies: quoting someone is not continuing a thread.

**What the distribution justifies.** Of the 50 items sampled, 21 were root
posts, 28 were replies naming the same account as the author (self-threads) and
1 was a reply naming a different account. So attaching a reply to the preceding
thread only when the target matches the author keeps 28 of 29 replies grouped
exactly as before and stops the remaining case, roughly 2% of items, from being
glued onto an unrelated conversation. The residual error is a self-thread
interleaved with another self-thread by the same author within one window, which
this feed's data cannot distinguish at all.
