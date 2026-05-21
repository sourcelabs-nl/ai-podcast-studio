# Deep-Dive Web Research

When `deepDiveEnabled` is set on a podcast, the script composer is given a `webSearch` tool backed by [Tavily](https://tavily.com). The LLM autonomously decides when to call it (typically 1-2 times for the most newsworthy story) to add outside context that isn't present in the source articles. The tool is capped at **3 calls per episode**.

## Configuration

```yaml
app:
  research:
    tavily:
      cost-per-call-cents: 1    # Tavily basic search ≈ $0.008/call, rounded up
    cost-buffer-cents: 5        # Added to the cost-gate estimate when deep-dive is on
```

## API key resolution

Resolved in the same precedence as other providers:

1. User-stored key under category `RESEARCH`, provider `tavily` (encrypted at rest).
2. `TAVILY_API_KEY` environment variable.

If no key is resolvable, generation still succeeds: the tool returns empty results and a single warning is logged per episode. Each call is cached on `(query_hash, max_results)` so repeated identical queries (across episodes) reuse the response without re-hitting Tavily.

## Episode response fields

Episode responses include `researchCalls` (number of `webSearch` invocations) and `researchCostCents` (Tavily cost only). The dashboard shows these on the episode detail page when `researchCalls > 0`. The toggle and a "Test Tavily" validation button live under **Podcast Settings > Research**.
