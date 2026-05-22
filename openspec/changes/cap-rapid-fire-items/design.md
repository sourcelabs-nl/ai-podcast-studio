## Context

The rapid-fire tier in `SubtopicPlan.from` was unconstrained: every article whose subtopic weight was at or below `rapidFireWeightThreshold` (plus the synthetic `Other` bucket) ended up in the segment, with no limit and no ordering. The composer prompt asked for "one to two sentences per article" inside a 15%-of-target-words budget, which is satisfiable only for a small number of items. With 15+ rapid-fire articles (typical for a busy news day), the LLM has two options: violate the per-item instruction (cram multiple items per sentence) or violate the budget. It chose cramming. The result reads like a CSV being read aloud and squashes high-impact stories that happen to land in low-weight subtopics (e.g. an Erdős breakthrough in a "benchmarks" bucket weighted 2).

Per user direction during planning: prefer fewer items with real explanation over breadth; dropped items should disappear from the script entirely rather than getting a "skipped today" mention.

## Goals / Non-Goals

**Goals:**
- Cap the number of rapid-fire items per episode with a sensible default and a per-podcast override.
- Prioritise items so the most important survivors are covered first.
- Give the LLM an arithmetic-consistent per-item word budget so it stops cramming.
- Keep the change surgical to the planner; composers stay structurally unchanged.

**Non-Goals:**
- Promoting high-score rapid-fire articles into the full-segment tier (considered during planning; deferred — adds a second decision axis and the cap+ranking alone fixes the observed defect).
- Surfacing dropped items anywhere in the UI ("articles excluded today" view) — user explicitly preferred clean drops.
- Changing how the full-segment tier is constructed.

## Decisions

**Cap location: in `SubtopicPlan.from`, not in the composers.**
Centralising in the planner means all three composers (`BriefingComposer`, `DialogueComposer`, `InterviewComposer`) benefit without duplication, and the kept set is reflected in `articleSubtopics` so downstream prompt sections (article summary block) don't include dropped articles.

**Ranking key: `(bucket.weight DESC, article.relevanceScore DESC nulls last, article.id ASC)`.**
- Bucket weight first: respects the podcast owner's stated priorities.
- Relevance score next: within a bucket, the scoring stage already ranked stories by importance; reuse that signal rather than inventing a new one.
- Article id as deterministic tie-break: ensures stable ordering across regenerations of the same set.

**Kept-article enumeration in the prompt.**
The prompt block lists kept items as a numbered list `N. [bucket] "title"` instead of just bucket-level counts. Surfaces the priority order to the LLM explicitly so the highest-priority item is covered first if the model still under-budgets later items.

**Per-item word budget = `rapidFireWordBudget / keptCount`, computed once in `buildSubtopicPlanBlock`.**
Replaces the vague "one to two sentences" wording. At the default cap of 6 and the default 15% budget of a 1750-word target, that's ~44 words per item — enough for a one-line *what* + a one-line *why it matters*. Integer division is fine; the prompt phrases it as "approximately" and "roughly".

**Default cap = 6, global override via `app.compose.rapid-fire-max-items`.**
Six items × ~40-50 words per item × ~10 seconds of audio per item ≈ a 60-second rapid-fire segment, which is the longest a single non-deep-dive segment should run before listener fatigue. Per-podcast override is nullable: `null` means "use the system default", which is the right backwards-compatible choice for existing podcasts.

**Drop dropped items entirely (no "also today" mention).**
Confirmed user preference. Simplest implementation: kept articles are removed from `articleSubtopics` and from the per-bucket article lists, so the upstream article summary block also stops including them. The article rows in the database are untouched (they remain associated with the source/scoring), they just don't appear in this episode's script.

**`SubtopicPlan.from` signature gains a 5th parameter rather than wrapping in a data class.**
Currently 4 params → 5. Borderline against the "5 params triggers data class" rule, but a data class wrapper here would only be used at three call sites that already destructure podcast/config in the same way. Defer the wrap until the next param would push it to 6.

## Risks / Trade-offs

- **[Risk] Drops can hide a genuinely important article when scoring is wrong.** → Mitigated by ranking on relevance score within bucket: a high-impact story in a low-weight subtopic still ranks above filler in the same bucket. Not fully mitigated across buckets — a weight-1 high-score article is still demoted under a weight-2 low-score one. Future follow-up (promotion across tiers) is captured as a non-goal here.
- **[Risk] Integer-division per-item budget can round down to 0 if `keptCount > rapidFireWordBudget`.** → Acceptable: only happens with absurdly small target word counts (<60 words target), where the rapid-fire tier shouldn't exist anyway. Not worth a special case.
- **[Trade-off] Per-podcast override stored as nullable means two layers of defaults (DB null → config default).** Adds a small lookup, gives podcast owners control without a migration backfill, and lets us tune the global default centrally without per-row changes.
- **[Risk] The reordered prompt instruction may change the LLM's behaviour for podcasts that were previously OK.** → Unlikely to regress: the new instruction is strictly more specific than the old one (exact count + per-item budget + ordering). For small rapid-fire tiers (1-3 items) the per-item budget is generous enough that behaviour matches the old prompt's intent.

## Migration Plan

1. Apply migration `V58__add_podcast_rapid_fire_max_items.sql` — adds nullable column, no backfill needed.
2. Deploy backend; existing podcasts immediately use the system default cap of 6.
3. Deploy frontend; podcast owners can now set per-podcast caps via the settings page.
4. No rollback complexity: the column is nullable and unused if the code is reverted; behaviour falls back to the old planner (which ignored cap entirely) without data loss.
