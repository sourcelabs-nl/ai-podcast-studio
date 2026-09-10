## Why

DeepSeek v4.1 Flash is out, and it is the better model for every stage this pipeline runs at a price the routing floor can actually reach.

Compose ran on `z-ai/glm-5.3` at $1.12/$3.52 per Mtok. GLM-5.3 was picked on reasoning and coding indices, which are not what this stage does: it writes prose. v4.1 Flash writes better prose, costs $0.30/$1.20 through the cheapest endpoint that clears the quantization floor, and carries a 1M context on four of its five eligible endpoints, so a large article set with research and history tool calls has room.

Filter and dedup ran on `deepseek/deepseek-v4-flash-0731` at $0.05/$0.16. v4.1 Flash is dearer there, and worth it: those stages decide what the episode is about, and a stronger model makes fewer bad calls on relevance and on what is a continuation of yesterday's story.

The catalogue price of $0.15/$0.60 is DeepSeek's own endpoint, which reports its quantization as `unknown` and is therefore rejected by the routing floor, as are Fireworks (unknown) and Morph (fp4). A live probe with the floor and the account's provider guardrails applied routed to Novita at $0.30/$1.20, and that is the price recorded.

## What Changes

- The `app.models.openrouter` registry SHALL include `deepseek/deepseek-v4.1-flash` at input 0.30, output 1.20 USD per Mtok, the cheapest endpoint that clears the routing floor.
- The `filter`, `dedup` and `compose` stage defaults SHALL all name `openrouter` / `deepseek/deepseek-v4.1-flash`, in `application.yaml` and in the code-level `StageDefaults` alike.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `model-registry`: the stage defaults now name a single model for all three stages, and the registry gains its entry.

## Impact

- Configuration only: `app.models.openrouter` and `app.llm.defaults` in `application.yaml`.
- Cost: roughly a cent more per episode on scoring, dedup and recap, largely paid back by compose leaving GLM-5.3. On the current publishing rate that is around 30 cents a month.
- Reasoning behaviour was probed live before the switch: at the compose stage's own medium effort the model returns a modest reasoning block (58 tokens, not a runaway default) and a proper `finish_reason`, and it honours an explicit effort of `none` at 0 reasoning tokens, which is what the structured stages ask for.
- A podcast with its own per-stage override is unaffected.
