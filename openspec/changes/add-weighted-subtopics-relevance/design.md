## Context

Today `ArticleScoreSummarizer` (Stage 1 of the LLM pipeline) issues one structured call per article that returns `{relevanceScore, summary}`. Articles passing `relevanceThreshold` flow into Stage 2 (briefing/dialogue/interview composers), where they are presented as a flat list and the LLM allocates script time however it sees fit. There is no machine-readable signal of editorial priority within a podcast's topic, so a noisy long-tail subtopic ("general AI news") can crowd out the stories the editor cares about most.

This design adds a single editorial control surface: a per-podcast map of subtopic name → importance weight (1-10). Weights drive script-time allocation only — they do not change which articles survive the filter (`relevanceThreshold` stays global). Low-weight subtopics are rolled into an explicit "and in brief" rapid-fire segment.

## Goals / Non-Goals

**Goals:**
- Let editors express which areas inside a podcast's topic deserve full coverage vs rapid-fire mention.
- Reuse the existing Stage 1 LLM call — no extra round-trips, no extra cost.
- Preserve backwards compatibility: podcasts with no subtopics behave exactly as today.
- Support all three composer styles (briefing, dialogue, interview) with the same pacing model.
- Keep the on-screen control simple: one key/value editor plus one int input.

**Non-Goals:**
- Threshold bias by subtopic (low-weight subtopics filtered harder). Out of scope; `relevanceThreshold` stays a single global value.
- Per-subtopic selection caps (max N articles per subtopic). Not v1.
- Topic hierarchy or nested subtopics. Subtopics are a flat list per podcast.
- UI for reordering subtopics. Order is irrelevant to pacing — weight is the only signal.
- Subtopic suggestions from the LLM. The editor authors the list manually.
- Multi-podcast templates for subtopic sets.

## Decisions

### Decision 1: Single LLM call returns relevanceScore + subtopic

The Stage 1 prompt is extended to include the podcast's subtopic list. The structured response gains a `subtopic: String?` field. The LLM picks the best-matching subtopic name from the list, or returns `null` if no listed subtopic fits (the article is on-topic but unclassified).

**Alternatives considered:**
- *Two-step (score then classify):* doubles LLM cost on Stage 1, harder to keep prompts in sync. Rejected for cost and complexity.
- *Embeddings + cosine match:* avoids extra prompt tokens but requires an embeddings pipeline and a synced vector for each subtopic. Over-engineered for a flat 5-10 element list.

**Why this choice:** the LLM already has the article body, the topic, and now the subtopic list in one prompt. Classification is a small marginal token cost (subtopic list is short and stable). Misclassifications are recoverable — the editor edits the subtopics list and regenerates.

### Decision 2: Weight controls script time only

Weight is an integer 1-10. The composer allocates word budget proportionally to weight within the "full segment" tier and emits a single "and in brief" segment for the rapid-fire tier. Weight does **not** adjust the relevance filter, does **not** cap article counts, does **not** reorder segments.

**Alternatives considered:**
- *Threshold bias:* `effectiveThreshold = base + (10 - weight) * k` would filter low-weight subtopics harder. Layerable later if pacing alone isn't enough.
- *Selection caps:* max-N per subtopic. Hard ceilings feel arbitrary; if 5 high-quality LLM stories are available in one week, the editor probably wants all 5.

**Why this choice:** "weight = editorial attention" is the simplest mental model. It maps directly onto a knob the composer already controls (word count per topic). Filter and cap mechanisms can be added later without changing the data model.

### Decision 3: Rapid-fire is explicit, threshold is per-podcast

The composer emits a labeled rapid-fire segment ("And in brief:" or style-appropriate equivalent) at the end of the script. Subtopics with `weight <= podcast.rapidFireWeightThreshold` are rolled into this segment, one short line per article. Default threshold: 3 (i.e. weights 1-3 are rapid-fire).

**Alternatives considered:**
- *Implicit compression* (no labeled segment, just smaller word budgets): preserves style purity but listeners can't perceive the editorial intent.
- *Derived split (top-half full, bottom-half rapid-fire):* adaptive but breaks at the edges (all-weight-10 → forced rapid-fire; all-weight-1 → forced full segments).
- *Hard-coded global threshold:* loses tuning flexibility; per-podcast taste varies (news-briefing vs casual round-up).

**Why this choice:** the explicit segment is what the editor is implicitly asking for when they say "rapid-fire." Per-podcast threshold matches the granularity of `relevanceThreshold` (same shape of knob, same place in the UI). All-weight-10 → no rapid-fire (correct: editor said everything is important). All-weight-1 → all rapid-fire (correct: editor said nothing is heavy).

### Decision 4: Uncategorized articles → synthetic "Other" bucket, weight 1

When the LLM returns `subtopic = null`, the article is grouped into a synthetic "Other" subtopic with effective weight 1. With the default rapidFireWeightThreshold of 3, "Other" rolls into rapid-fire automatically. Editors who want unclassified articles excluded entirely can raise `relevanceThreshold` or add a catch-all subtopic and set its weight to whatever they want.

**Alternatives considered:**
- *Drop unclassified:* silently loses on-topic edge cases. Surprising and lossy.
- *Keep in main flow at full weight:* defeats the weighting; LLM "I'm not sure" answers would always get premium time.
- *Force the LLM to pick the closest:* produces weird mappings ("AI ethics" story crammed into "LLM releases" because nothing else fits).

**Why this choice:** preserves the safety net — nothing on-topic is lost — and the editorial default (rapid-fire) matches the intent that unclassified stories are not the headline.

### Decision 5: Word budget formula

For the full-segment tier:
```
fullSubtopics = subtopics where weight > rapidFireWeightThreshold
sumOfFullWeights = sum of weights in fullSubtopics
fullSegmentWordBudget = targetWords * (1 - rapidFireBudgetFraction)
wordsForSubtopic(s) = fullSegmentWordBudget * s.weight / sumOfFullWeights
```

The rapid-fire tier gets a fixed fraction of `targetWords` (default 15%, configurable via `app.compose.rapid-fire-budget-fraction`). The composer prompt receives the per-subtopic word target and the rapid-fire word target as explicit instructions.

**Why this choice:** proportional-by-weight matches the user's mental model (weight 10 ≈ 2× the time of weight 5). A fixed rapid-fire fraction prevents the rapid-fire segment from eating the script when there are many low-weight subtopics.

### Decision 6: Backwards compatibility via empty map

When `podcast.subtopics` is empty (or null), the pipeline emits the existing prompt (no subtopic list, no `subtopic` field in the response schema) and the composer uses today's flat layout. No data migration of existing podcasts is required.

### Decision 7: Flat-layout fallback when full-segment tier is empty

After grouping articles by subtopic, if no subtopic ends up in the full-segment tier (either because every configured subtopic's weight is at or below `rapidFireWeightThreshold`, or because every article classified as `null` and only the "Other" bucket has content, or because the only matching subtopics happen to be rapid-fire-tier), the composer SHALL fall back to today's flat layout — no per-subtopic segments, no rapid-fire label, just a normal script over all available articles.

This is a single rule that subsumes several degenerate cases:
- *All articles classified as null:* "Other" is the only bucket, weight 1, no full tier → flat layout. The editor's subtopics didn't fit this week's news; we don't punish them with an all-rapid-fire episode.
- *All subtopics weight ≤ threshold:* the editor said "nothing is heavy this week" — write a normal flat script rather than tagging everything as rapid-fire.
- *High-weight subtopics matched zero articles:* if only low-weight subtopics caught content, treat it as a regular episode rather than a rapid-fire-only one.

**Alternatives considered:**
- *Always emit a rapid-fire segment even when it's the only segment:* contrived and confusing — a podcast whose entire content is in "And in brief" is a bad listener experience.
- *Promote "Other" to full-segment when it's the only bucket but keep rapid-fire for the all-low-weight case:* two rules instead of one, and the two cases are editorially indistinguishable from the listener's perspective.

**Why this choice:** the rule "no full tier → flat layout" is a single, predictable invariant. Editors can reason about it without enumerating edge cases. The rapid-fire segment exists in *contrast* to full segments — without a full segment, there's nothing to contrast against, so the label loses meaning.

## Interactions with deep-dive web research

The `add-deep-dive-research-tavily` change already shipped a `webSearch` tool that the composer can call (capped at 3 invocations per episode) when `podcast.deepDiveEnabled` is true. There is no data-layer conflict with this change — separate tables, separate fields, independent flags — but the two features share the composer prompt and need to coordinate there.

When both `deepDiveEnabled = true` AND `subtopics` is non-empty, the composer prompt SHALL:

1. **Restrict webSearch to full-segment articles.** The tool is useless for rapid-fire one-liners — you cannot fit fetched context into a one-sentence mention. The instruction block SHALL explicitly tell the LLM: "only invoke webSearch for stories you will cover in a full segment; do not webSearch for stories destined for the rapid-fire segment."
2. **Bias selection toward higher-weight subtopics** while letting newsworthiness still tiebreak. Phrasing: "use webSearch for the most newsworthy story among the full-segment subtopics, preferring higher-weight subtopics when stories are comparable." Weight is editorial importance; news-of-the-day is the LLM's judgement — both signals matter.
3. **Keep the tool budget episode-wide** (3 calls total), not per-subtopic. Multiplying the cap by number of subtopics would blow past the cost gate and contradict the existing buffer assumption.
4. **Leave the 5¢ deep-dive cost buffer unchanged.** Subtopics don't add LLM round-trips or research calls — they're a prompt-shape change, not a tool-use change.

When `subtopics` is empty (feature disabled), the existing deep-dive prompt block is used verbatim. When `deepDiveEnabled = false`, the subtopics prompt is used without any webSearch block. The two flags compose orthogonally; only the both-on cell needs the additional constraints above.

This guidance also applies to the full-tier-empty fallback (Decision 7): in that mode the composer reverts to the flat layout, and if deep-dive is on, today's deep-dive prompt block is appended unchanged (since there's no full/rapid-fire distinction to coordinate against).

## Risks / Trade-offs

- **[LLM misclassifies the subtopic]** → mitigation: keep the subtopics list short and orthogonal; surface the chosen subtopic in the episode-articles view so the editor can spot patterns and refine the list. Misclassifications only affect pacing, not whether the article appears.
- **[Editor authors overlapping subtopics like "LLMs" and "AI model releases"]** → mitigation: documented guidance in the UI helper text ("keep subtopics short and non-overlapping"); the LLM will pick one consistently, but split coverage is editorially confusing.
- **[Dialogue/interview styles awkward with rapid-fire]** → mitigation: each composer gets a style-appropriate rapid-fire phrasing instruction ("Host briefly mentions a few more stories," "Interviewer: quick lightning round before we wrap"). Existing composer tests assert the segment is emitted; new ones assert style fit.
- **[Word budgets drift in practice]** → mitigation: LLMs are loose about exact word counts. The contract is "relative attention," not "exactly N words" — drift is acceptable as long as high-weight subtopics consistently get more space than low-weight ones.
- **[Existing podcasts inadvertently regress]** → mitigation: empty subtopics map is the default; integration tests assert a podcast with no subtopics produces a script byte-identical to the pre-change prompt path.

## Migration Plan

1. Flyway migration `V<next>__add_weighted_subtopics.sql`:
   - `ALTER TABLE podcasts ADD COLUMN subtopics TEXT;` (nullable, JSON)
   - `ALTER TABLE podcasts ADD COLUMN rapid_fire_weight_threshold INTEGER NOT NULL DEFAULT 3;`
   - `ALTER TABLE articles ADD COLUMN subtopic TEXT;` (nullable)
2. Deploy backend with the new fields wired through DTOs and the scorer/composers; behavior unchanged for podcasts with empty subtopics.
3. Deploy frontend with the new Subtopics editor in podcast settings.
4. Editors opt in per podcast.

Rollback: drop the three columns; revert backend + frontend. The new prompt code path is only exercised when `podcast.subtopics` is non-empty, so a partial rollback (frontend only) is safe — backend continues to accept and ignore the column.

## Open Questions

- Should the `Other` bucket label be configurable per podcast (e.g. "In other news")? Defer to v2 unless feedback says the default phrasing feels off.
- Should the dialogue/interview composers emit the rapid-fire segment as a single host monologue, or alternate speakers? Lean monologue (one host hands off), revisit after the first manual smoke test.
- Do we surface the chosen subtopic on the episode-articles view? Probably yes for editorial visibility, but it's a small frontend change that can ride along or land separately.
