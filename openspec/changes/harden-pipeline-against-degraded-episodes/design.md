## Context

See proposal.md (Why) for the two incidents this change answers.

Three constraints shape the approach:

- SQLite carries both uniqueness constraints already (`post_articles(post_id, article_id)` and
  `articles(source_id, content_hash)`). Nothing about the schema is wrong; the write paths simply
  assume they are the only writer.
- The dedup stage runs under a Resilience4j retry that escalates the prompt on each attempt. Raising
  from inside the parse is therefore not a failure but a request for another attempt, and only the
  exhausted retry becomes an episode failure. This is the existing mechanism the salvage floor uses.
- The dedup prompt is the contract being validated. It states when an empty `selectedArticleIds` is
  allowed (`CONTINUATION` with no new information) and when it is not (`NEW`, which keeps all
  articles or the three most comprehensive). Validation reads that contract rather than inventing a
  separate notion of a plausible response.

## Goals / Non-Goals

**Goals:**

- Make a concurrent aggregation converge instead of failing an episode.
- Detect a well-formed dedup response that is nonetheless unusable, using a rule derived from the
  prompt rather than a tuned threshold.
- Stop a degraded episode at the last gate before listeners see it.

**Non-Goals:**

- Serialising the two aggregation paths. Idempotent writes make the race harmless, which is cheaper
  and less brittle than a lock spanning transactions; under SQLite's single-writer model a lock
  inside the transaction would not even close the window, because each writer's read happens before
  the other commits.
- Changing the dedup model or its prompt. The model returning empty `NEW` clusters is a fault worth
  detecting whatever model runs the stage.
- Judging episode quality. The publishing floor counts articles; it does not assess the script.

## Decisions

**Idempotency at the SQL statement, not in application logic.** The link insert becomes
`INSERT OR IGNORE`, so the database decides and no read-then-write window exists. The alternative,
checking for an existing link first, reintroduces exactly the race being fixed: both writers see no
link and both insert. The article insert cannot use the same trick because the generated id is
needed, so it keeps read-first and falls back to re-reading the stored row when the insert hits the
constraint. Catching the duplicate is safe inside the transaction: SQLite's default `ON CONFLICT
ABORT` rolls back the statement, not the transaction, and Spring only marks a transaction
rollback-only at the interceptor boundary, which is not crossed here.

**Degeneracy is measured on `NEW` clusters, not on the overall selection count.** A rule like
"reject when fewer than N articles are selected" would break the legitimate quiet day the spec
already protects, where every cluster is a `CONTINUATION` with nothing new and zero selections is
the correct answer. Keying on `NEW` clusters separates the two cases exactly: an empty `NEW` cluster
is always a contract violation, an empty `CONTINUATION` cluster never is.

**The threshold is a majority of `NEW` clusters, not any single one.** Rejecting a response over one
sloppy cluster would burn a retry and eventually an episode to save a single topic, which is worse
than dropping that topic. Rejecting only when most `NEW` clusters are empty separates a slightly
imperfect response from a broken one: the incident sat at 33 of 34, while a response with 2 of 30
empty is worth keeping. A minority is dropped with a WARN so the loss is visible in the log rather
than silent.

**`app.publishing.min-articles` defaults to 5.** Chosen to catch the failure mode (an episode of 1
to 4 articles is the symptom of an upstream fault, never a real day) while leaving normal and even
thin days publishing unattended. A higher floor such as 10 would put ordinary quiet days in the
manual queue; a lower one such as 3 would have let an episode of 4 articles through. The floor is
configuration, so it can move without a code change.

**The floor lives in the auto-publish listener, not in the publishing service.** A manual publish
must stay possible: the user looking at a two-article episode and deciding to ship it is a
legitimate act, and the service is shared by both paths. Placing the check in the listener keeps the
restriction on the automatic path only, matching the spec.

## Risks / Trade-offs

- **A genuinely quiet day now needs a manual publish.** → The floor is configurable and defaults low
  enough (5) that this should be rare; the skip is logged at WARN with the article count so the
  reason is unambiguous.
- **A degenerate response on every retry now fails the episode instead of publishing something.** →
  That is the intent, and it matches how the dedup stage already treats an unsalvageable response. A
  failed episode is retryable from the dashboard; a published one-article episode had to be
  unpublished by hand.
- **The majority rule could pass a response with just under half its `NEW` clusters empty.** → Such a
  response still composes from most of its topics, and the dropped ones are logged. Tightening the
  rule trades this against failing episodes over minor model sloppiness.
- **Catching a duplicate-article insert could mask a real constraint problem.** → The fallback
  re-reads and only proceeds when the stored row is actually found; if it is not, the original
  exception is rethrown.

## Migration Plan

No migration. Both constraints already exist, and the change only alters how the write paths behave
when they are hit. Deployment is a restart. Rollback is reverting the code: no data written under
this change is shaped differently from data written before it.
