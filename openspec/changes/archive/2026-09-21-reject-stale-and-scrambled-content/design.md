## Context

All four faults share a shape: a stage produced something wrong in a way the next stage could not
detect, so nothing failed and the episode shipped. Each fix therefore adds the missing signal at
the stage that still has the information, rather than adding a check further downstream where the
evidence is already gone.

## Decisions

### The scoring stage is the only place an article can be placed in time

Compose reads article bodies only when the run is smaller than `app.briefing.full-body-threshold`
(default 5). A normal 40-article day is far above that, so compose sees summaries and nothing else.
Any decision that depends on the body text must be made while the body is still in hand, which is
the scoring call. That is why the classification lives there and is persisted, rather than being
inferred later from the summary, and why the summary rules changed in the same prompt: a summary
that asserts a release the article did not report cannot be corrected downstream.

### Drop evergreen pages rather than flag them

An evergreen page carries no event, so there is no version of a news episode it belongs in. Flagging
it and letting compose decide was considered and rejected: it keeps a class of content whose only
possible treatment is a product description, and relies on the compose prompt honouring a marker
among the many rules it already carries.

The cost is accepted and real: a genuinely new project whose launch *is* its homepage looks
identical to a project that has existed for years, and both are dropped. Such a launch is normally
also covered by a source that does report it as an event, and that coverage is unaffected.

### An unknown classification is kept

Null means the model did not answer, answered with something unrecognised, or the article was
scored before the field existed. All three are "we do not know", and dropping on "we do not know"
would silently delete content on a model hiccup. This matches the existing treatment of an article
with no `published_at`, which is kept for the same reason.

### Dedup recalls topics, not only titles

`episode_articles.topic` already stores the label this stage assigned on earlier runs, and it is a
better record of what the show said than the source headlines are: it describes the story as the
show framed it, and survives a topic whose source article was headlined about something else. Both
blocks are sent. Titles still catch a topic from before labels were persisted, and carry the source
domain, which the labels do not.

Topics are not capped the way historical articles are. One label per cluster is an order of
magnitude fewer lines than the articles behind it, and a label dropped is a topic the filter
forgets.

### The opener is authoritative when a closer does not match

For `<expert>…</interviewer>`, either tag could be the mistake. The opener is preferred because it
sits immediately after the previous turn's closer, inside text whose speaker is already established
by the alternation, while the closer is demonstrably the tag the model got wrong. The reverse
reading cannot be excluded for any single turn, so every rewrite is logged at WARN naming both
roles, and a model that starts doing this routinely becomes visible rather than silently corrected.

### Repair before validating, not instead of it

The repair runs first and validation sees the repaired script, so a fault the pipeline can fix never
costs a second compose call, the most expensive in the pipeline. What survives repair is genuinely
ambiguous — two openers in a row give no way to know where the first turn ended — and guessing there
would invent speaker attribution rather than recover it. Those fail the advisor and are re-prompted,
and failing that they fail the episode, which is the existing fail-closed contract.

### Why the script stays free text rather than becoming structured output

Returning the script as JSON turns would make this bug class impossible, and Spring AI supports it
(`BeanOutputConverter`, already used by the scoring stage). It was measured against the compose model
on this episode's own articles before this change was settled: see
`knowledge/evals/structured-output-for-scripts.md`.

The measurement changed the reasoning rather than confirming it. The objection from the dedup stage's
truncated responses did not reproduce at all: three structured runs out of three parsed cleanly and
stopped normally, well inside the token budget. Meanwhile free text malformed its speaker tags in
three runs out of three, always by closing an `<expert>` turn with `</interviewer>`, which makes the
repair above load-bearing rather than defensive.

What blocks adoption is a different problem the measurement found: structured output loses control of
script length, running between one and a half and three and a half times the target where free text
holds a consistent third over. Length drives TTS, which costs about 50 cents an episode against
compose's 8, so the format change would cost more than the entire LLM budget it sits in. Adopting it
therefore needs length control solved first, which is its own change and not an incident fix.

## Risks

- A model that reliably misclassifies a real announcement as `EVERGREEN` would silently drop it. The
  eligibility log names every dropped article with its URL so this is visible rather than inferred
  from a thin episode.
- Feeding covered topics makes the filter more willing to call something a `CONTINUATION`, which on
  a quiet day could select fewer articles. The existing degenerate-response guard already rejects a
  response whose `NEW` clusters are mostly empty, and an all-`CONTINUATION` day remains valid.
