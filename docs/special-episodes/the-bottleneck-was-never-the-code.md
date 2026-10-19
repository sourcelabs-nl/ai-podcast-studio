# The Bottleneck Was Never the Code

Podcast script · October 4, 2026 · Stephan Oudmaijer

## Episode overview

Format: an extra deep-dive episode, outside the daily briefing. It takes one subject and stays with it for about 21 minutes. The regular daily episode follows as usual.

Core thesis: AI made writing code cheap, but the bottleneck was never the code. It is the chain, the organisation and the understanding around it.

| Part | Topic | Time |
|---|---|---|
| Cold open | The paradox in one number | 2 min |
| Segment 1 | Coding faster, not shipping faster | 3 min |
| Segment 2 | The counterpoint, and the hard data | 3 min |
| Segment 3 | Models converge, costs collapse | 4 min |
| Segment 4 | "Slow down," they said. Then they shipped. | 4 min |
| Segment 5 | The organisation is the bottleneck | 3 min |
| Closing | The one question that matters | 2 min |
| **Total** | | **21 min** |

Earlier daily episodes that this deep dive builds on: Opus 5.5 special (Sep 22), Sonnet 5.5 (Sep 29), GPT-6.1 Sol and Dots (Sep 30), Gemini 4 Argon (Oct 1), GPT-6 Astra (Sep 2 and Sep 4), GPT-5.6 (Jun 29), Claude Fable 5.1 and Mythos 5.1 (Sep 2), agent pull request quality (Sep 17), and the coding agent session dataset (Oct 2).

## Cold open (2 min)

"Welcome to a special episode of the Daily Agentic AI Podcast. This is an extra deep dive: one subject, a bit more time than usual, and our regular daily episode follows as always.

Here's a number that should bother every engineering leader. An MIT and Wharton study of 100,000 GitHub developers found that adopting the full stack of AI coding tools roughly triples their commits. But they work on only about 50% more projects. And releases? Only about 30% more.

Source: Demirer, Musolff & Yang, CEPR/VoxEU, June 2026

So we're writing code faster than ever. We're just not shipping it faster. And that gap, between writing code and shipping, is what this whole episode is about.

Because I think the bottleneck was never the code. Let's dig in."

Production note: pause after "Only about 30%". Let the number land.

## Segment 1: Coding faster, not shipping faster (3 min)

### Talking points

- Writing code was never the main bottleneck. Review, testing, integration, deployment and decision-making still run at human speed.
- The O-ring logic: if a process has complementary stages and AI speeds up only some, output is capped by the slowest stage. The one humans still do.
- So the bottleneck didn't disappear. It moved downstream. More pull requests, same throughput.
- The warning sign was there early: Google's 2024 DORA report found that a 25% rise in AI adoption went with an estimated 1.5% drop in delivery throughput and a 7.2% drop in delivery stability.
- Developers *feel* much faster than the data says they are. Leaders who manage to the feeling keep funding the wrong thing.
- The data from our own daily episodes points the same way. A large dataset of real coding agent sessions (Oct 2) found that only 59% of agent-written code gets committed, with frequent user pushback and more security vulnerabilities than human-written code. A study of more than 37,000 agent-authored pull requests (Sep 17) found that quality varies by vendor, not by agent versus human. Another (Sep 24) found agent pull requests need follow-up fixes at 1.6 times the odds of human ones.

### Fresh this week (October 2026)

- Still true right now: industry roundups from October 1 report that faster coding is being offset by the time spent on code review.
- The tooling is adapting to it: from October 19, GitLab meters agent traffic per user and per group, so leaders can see what agents actually cost and produce.

### Statement to discuss

"If you measure adoption instead of outcomes, you're optimising the wrong part of the pipeline."

## Segment 2: The counterpoint, and the hard data (3 min)

### The counterpoint

- The studies showing 10% gains aren't wrong. They're measuring the wrong thing.
- DX tracked 400+ engineering organisations over 16 months: AI usage up 65%, median PR throughput up just under 8%. Most land between 10% and 15%. Real, but modest.
- The argument: the gain isn't speed on old work. It's what developers now *dare to attempt*. Bigger scope, more experiments.

### Fresh this week (October 2026): the strongest counterpoint yet

- At Rails World on September 23, DHH said 37signals has gone "pencils down": they now generate all their code with AI. Coming from one of AI coding's loudest sceptics, that stirred the whole industry.
- Agents now run autonomously for hours on real engineering tasks, without constant supervision. Our own episodes show what that looks like: GitHub migrated its Copilot agent runtime from TypeScript to more than 800,000 lines of Rust, mostly written by AI agents with one supervising developer (Sep 17). Google's ECO optimizer landed over 6,000 verified performance commits (Sep 18).
- Host question: if a small, disciplined team can go fully AI-generated, is the paradox a technology problem, or a big-company problem?

### Even the researchers behind the paradox are optimists

- The MIT and Wharton authors call +50% projects and +30% releases a remarkable effect for any workplace technology.
- Their effects grew with every major model release, and tool makers are now automating review and testing too. They expect the bottleneck to shift, as it did with earlier general-purpose technologies.
- Host question: is the paradox a law, or just a snapshot of a transition that is already moving?

### The hard data pushing back

- Task level: controlled experiments show 15% to 50% gains. Enterprise level: MIT's NANDA initiative (2025) found 95% of generative AI pilots had no measurable effect on profit and loss. Its method is disputed, so treat it as a signal, not a law.
- Quality: in Sonar's 2026 State of Code survey (1,100+ developers), 96% don't fully trust AI-generated code, yet only 48% always verify it before committing, and 38% say reviewing AI code takes more effort than a colleague's.
- Cost: Uber burned through its entire 2026 AI coding budget in four months. Microsoft cancelled most Claude Code licences in its Experiences + Devices division by June 30 and moved engineers to its own GitHub Copilot CLI: partly cost, partly consolidation.

### Statement to discuss

"AI doesn't make your team excellent. It amplifies what's already there, good and bad."

## Segment 3: Models converge, costs collapse (4 min)

### At the top, it's neck and neck

- Claude Opus 5.5 and GPT-6.1 Sol: on several benchmarks their score ranges overlap. On DeepSWE, Opus 5.5 scored 74.2% versus 74.1% for OpenAI's flagship. A statistical tie.
- Release pace is wild: 20+ model releases in two weeks in September 2026, with a 119x price spread and a new floor of $0.10 per million tokens.

### From below, open-weight is catching up

- Kimi K3 (2.8T parameter MoE, open-weight) debuted at #3 on the Artificial Analysis Intelligence Index on July 17, behind only Claude Fable 5 and GPT-5.6 Sol, at roughly half the cost per task of Opus 4.8. Weights followed on July 27.
- Grok 4.5 delivers 91% of the top score at 88% lower output cost.
- GLM 5.2 broke through on planning and long-horizon coding, the exact area where closed models held their edge.

### Why self-hosting becomes real

- Open-weight inference runs 8 to 12x cheaper than comparable frontier APIs.
- Coding agents are token furnaces. Break-even for self-hosting sits around 10 to 30 million tokens a day, which agent-heavy teams hit fast.
- Data sovereignty: "No data processing agreement beats: the packets never left the building."

### Fresh this week (October 2026)

- Closed models are getting cheaper too: GPT-6.1 Sol delivers near-Astra coding at $2/$10 per million tokens versus $10/$50, and Opus 5.5 cut list prices 20% to $4/$20, which Anthropic says works out to about 40% lower cost on typical workloads.
- Open-weight leaderboard as of October 2: Xiaomi's MiMo-V2.6-Pro leads, ahead of Qwen3.8 Max. GLM-5.3 tops the open-weight coding benchmarks, and DeepSeek V4-Pro scores 80.6 on SWE-Bench.
- Self-hosting goes mainstream: on October 1 IBM launched a self-hosted version of its coding agent Bob, so banks and governments can run it fully air-gapped.
- The newest frontier model on paper is Google's Gemini 4 Argon, announced September 30, with a one million token output limit, strong benchmark results and reported internal agent-driven kernel migrations. But you can't use it yet. Access is limited to trusted cyber defenders through Google's Fairwind program, with no public date, and Google is in the US government's voluntary pre-release review process. It belongs in the "capability" column, not the "available to your team" column, and that difference is the point of this segment.
- **The honest nuance:** agent *reliability* still sits with the closed frontier, and Meta stepped back from open-weight frontier models (Llama 5 never shipped). The gap is closing on benchmarks faster than on long agentic work.

### Statement to discuss

"The gap isn't a gap anymore. It's a margin, and it shrinks every two-week release cycle."

## Segment 4: "Slow down," they said. Then they shipped. (4 min)

The labs say they want to slow down while releasing faster than ever. Organisations can't even keep up with last month's model.

### The pledge

- September 12: Anthropic CEO Dario Amodei published "We Must Pace the Frontier", calling on the industry to slow the rate of capability gains. The heads of OpenAI, xAI and Google DeepMind agreed within hours.
- What was actually promised: no release delay. The one concrete step is outside evaluators with real access inside the labs, not slower release timing.

### The reality

- Ten days later Anthropic shipped Opus 5.5, just 21 days after Fable 5.1 and Mythos 5.1. Anthropic's cadence went from roughly quarterly in 2025 to almost monthly in 2026. OpenAI shipped GPT-6 Sol and Luna the same day.
- By October 2, new frontier models had come from at least five major labs, each one faster, cheaper or more capable than the last.
- The cynical read, from experts: most enterprise clients don't come close to using current models' capacity anyway, and a slowdown would protect margins ahead of IPOs and invite rules that hit cheaper open-weight rivals.
- The fair counterpoint: pacing was framed as safety work catching up with capabilities, not a release freeze. Whether that holds is something to watch, not to assume.

### What the labs actually slow down: access

The one place the labs do slow down is not the release date. It is who gets the model first. This pattern has been visible in our daily episodes for months.

- June 29: OpenAI previewed GPT-5.6 (Sol, Terra, Luna). The launch was meant to be open access, but at the request of the US government it started as a limited preview among a small group of trusted partners, following the earlier controls on Anthropic's Mythos.
- September 2: Anthropic shipped Fable 5.1 and Mythos 5.1 as one model with two safeguard layers. Fable is generally available. Mythos has looser cyber and biology guardrails and stays restricted to vetted US organisations in cybersecurity and life sciences. The same day, OpenAI said GPT-6 Astra is the first model ever designated Critical under its Preparedness Framework for cyber capability: it can find previously unknown flaws and build working exploits against well-protected systems without step-by-step human guidance.
- September 4: Astra launched hosted-only and phased. First vetted organisations through OpenAI's Trusted Access and Daybreak programs, then ChatGPT paid plans, then the API. The standard release refuses advanced cybersecurity work, including exploit discovery. The full capability sits behind an application-based cybersecurity program.
- October 1: Gemini 4 Argon follows the same shape. Trusted defenders first, through Fairwind, before any public API.

So "pacing" in practice means the cadence of releases stays, and the most cyber-capable models reach defenders before they reach everyone else. Host question: good policy, or theatre?

### OpenAI: same claim, one real exception

- Altman backed the pacing call and said OpenAI would match Anthropic's step on independent evaluators.
- On September 28 OpenAI scrapped GPT-6.1 Astra, planned for October. It "didn't quite meet the bar" on staying within scope and authorisation. The first model OpenAI is known to have cancelled outright after announcing the plan, as opposed to restricting its access.
- The next day at DevDay: GPT-6.1 Sol, one week after GPT-6 Sol, plus the always-on agent Dots.
- Altman called holding Astra "normal course", saying OpenAI has "many great new models to come".
- Host point: pacing in practice means skipping one model while shipping the next one. The cadence doesn't change; only which model ships, and who may use it first.

### The other side: organisations can't keep up

- Every new release resets evaluations, governance, cost models and team habits. Most organisations haven't finished adapting to the previous one.
- Any enterprise whose roadmap tracks one lab's releases inherits that lab's decisions. The answer is model-agnostic infrastructure: swapping a model should be a config change, not a project.
- Host's own experience: this show is produced with a pipeline where each stage (scoring, deduplication, script writing, recap) has its own configurable model, and cost is tracked per call. When a new model lands, trying it is a setting, not a rewrite. That is the practical meaning of model-agnostic.

### Statement to discuss

"The labs won't slow down, and most organisations can't speed up. That gap is where the real risk lives."

## Segment 5: The organisation is the bottleneck (3 min)

### Nobody knows what agentic actually means

- 42% of organisations are still building an agentic roadmap. 35% have no formal strategy at all.
- Only 11% of organisations actively run agentic systems in production (Deloitte). Gartner expects 40%+ of agentic projects to be cancelled by end of 2027, citing rising costs, unclear business value and weak risk controls. Not the models.
- Host's view, from my own experience: many organisations hire expensive consultancies, who too often deliver a demo that works in the showroom but never becomes a platform. Agents get treated like traditional software: no governance, no integration layer, no data discipline.
- Fair counterpoint: research cited by Deloitte (itself a consultancy) found pilots built with external partners were about twice as likely to reach deployment as internal builds. The partner isn't the problem; a demo without a platform behind it is.

### Tech runs ahead, the business can't keep up

- In LinearB's data (2.7M pull requests, 253 orgs), the heaviest AI users merged code at 2.3x their rate of a year earlier, while non-users stayed flat. A correlation, not proof. Meanwhile 85% of employees have AI access, but only 25% use it regularly.
- Most companies are stuck bolting AI onto individual workflows. The leaders embed it where requirements become designs, and designs become code. Engineering is there; product and business are not.
- The new risk: **comprehension debt**. Engineers who ship fast but can't explain why something broke.

### "We're going all-in on agentic." Here's 5 euros a day.

- Agentic workflows use many times more tokens than a chat query. LinearB's 2026 data: about $50 per developer per month in a typical org, $152 in the top 30%, and $481 (about $35 per coding day) in the top 10%, still under 4% of a developer's fully loaded cost.
- So 5 euros a day covers assisted coding fine. What it caps is exactly the heavy agentic work leadership claims to want: the top users spend about seven times that.
- Uber went the other way: Claude Code adoption jumped from 32% to 84% of 5,000 engineers, and the annual AI budget was gone by April.
- Both extremes miss the point. Starving agents isn't strategy, and neither is an open tap. You need cost governance designed in from day one.

### Fresh this week (October 2026): security is the new bottleneck

- About 13,000 internal screenshots from 300+ organisations leaked via coding agents into public GitHub repos.
- "Plugin4Shell": a zero-click remote code execution flaw found in the four most popular coding agents, affecting millions of agents.
- The first formal US regulatory probe into rogue AI agents, after incidents of unexpected autonomous behaviour. Nvidia shipped tools to keep agents inside their sandbox.
- Host point: this is exactly what happens when tech teams move faster than governance. The organisations that designed guardrails in from day one are the ones not in this week's headlines.

### Statement to discuss

"You can't be agentic on 5 euros a day, and you can't be agentic with a business that still works like it's 2019."

## Closing (2 min)

"So let's pull it together. We code faster but don't ship faster. The models are converging and getting cheaper every few weeks. Open-weight is basically at the frontier. Capability is no longer the differentiator.

And the most capable models are not even available to everyone: the labs pace access, not releases.

What's left is everything around the model: your delivery chain, your governance, your token budget, and whether the business side can keep up with what engineering can now build.

The question for the next few years isn't whether AI makes you faster. It's whether you can redesign your organisation fast enough to actually cash in that speed.

And to be fair to the optimists: the people who measured this paradox expect it to shrink as AI moves into review, testing and release too. So the bottleneck isn't fixed. It moves. The real question is whether we move with it.

The bottleneck was never the code. It's us.

That was our extra deep dive. The regular daily episode follows as usual."

Call to action: ask listeners where the bottleneck sits in their organisation.

## Fact check (October 4, 2026)

The core claims are checked at the original source. The claims marked "single source" are fine to use, but attribute them on air.

Who's talking. DX, LinearB and Sonar sell measurement and code-review tools, so "the bottleneck is review" suits their business; their data is used here, but name that on air. Deloitte, quoted on agentic failures, is a consultancy. This script was researched with Claude, made by Anthropic, which Segment 4 discusses; read that segment with that in mind. The show itself is produced with AI models from the labs it covers.

| Claim | Status | Checked against |
|---|---|---|
| 3x commits, +50% projects, +30% releases | Verified, reworded | CEPR / NBER study |
| DX: usage +65%, throughput just under +8% | Corrected (was 15 months, 7.76%) | DX |
| "41% more bugs" attributed to SonarSource | Wrong, removed (it was a 2024 Uplevel study) | Sonar 2026 survey |
| "89% of agent pilots stall" (Gartner/IDC) | Untraceable, replaced by Deloitte's 11% in production | Deloitte |
| "No lab delayed a release" | Wrong, OpenAI held back GPT-6.1 Astra | CNN |
| "OpenAI's first held-back model" | Reworded: the first announced model OpenAI cancelled outright. Restricted or phased access has been used since June (GPT-5.6, Mythos 5.1, Astra) | Our daily episodes of Jun 29, Sep 2, Sep 4 |
| Gemini 4 Argon is the newest frontier model | Qualified: access limited to trusted defenders via Fairwind, no public date | Our daily episode of Oct 1 |
| Astra first model at the Critical cyber tier; phased access via Trusted Access and Daybreak | Verified in our daily episodes, single source per episode | Our daily episodes of Sep 2 and Sep 4 |
| GPT-5.6 started as a limited preview at the US government's request | Verified in our daily episode, reported by the Washington Post | Our daily episode of Jun 29 |
| Gartner: 40%+ agentic projects cancelled by 2027 | Verified, reasons corrected | Deloitte, citing Gartner |
| Deloitte: 42% building roadmap, 35% no strategy | Verified (2025 survey) | Deloitte |
| LinearB: heavy AI users 2.3x merge rate | Verified (correlation) | LinearB |
| 5 euros a day is far too little | Nuanced: enough for typical use, a cap on heavy agentic use | LinearB |
| Opus 5.5: 20% list cut, ~40% lower typical cost | Verified, clarified | Betanews |
| Kimi K3 #3 on Artificial Analysis | Verified | Artificial Analysis |
| Microsoft cancelled Claude Code licences | Verified, scoped to one division | People Matters |
| Amodei essay Sept 12, Opus 5.5 Sept 22, GPT-6.1 Sol Sept 29 | Verified | The Register, CNBC |
| DORA 2024: -1.5% throughput, -7.2% stability | Verified via secondary source | byteiota |
| DeepSWE 74.2% vs 74.1%; Grok 4.5 at 91% for 88% less | Single source | Benchmark aggregators |
| Open-weight 8 to 12x cheaper; break-even 10 to 30M tokens/day | Single source, vendor estimates | Industry blogs |
| 85% have AI access, 25% use it regularly | Single source | MindStudio, citing IBM |
| 13,000 leaked screenshots, Plugin4Shell, US agent probe | News roundups, not primary | AI news briefs |

## Sources (show notes)

### October 2026

- [AI Agents News Brief, Oct 2 2026](https://aiagentsdirectory.com/news/ai-agents-news-brief-october-2-2026)
- [AI Agents News Brief, Oct 3 2026](https://aiagentsdirectory.com/news/ai-agents-news-brief-october-3-2026)
- [MarketingProfs AI Update, Oct 2 2026](https://marketingprofs.com/opinions/2026/56056/ai-update-october-02-2026-ai-news-and-views-from-the-past-week)
- [Adversa: AI coding agent security, October 2026](https://adversa.ai/blog/top-ai-coding-agent-security-resources-october-2026)
- [AI Agents News, week of Oct 3 2026 (GitLab metering)](https://aiagentstore.ai/ai-agent-news/this-week)
- [BenchLM open-weight leaderboard, Oct 2026](https://benchlm.ai/best/open-source)
- [Thunder Compute: Best open source LLMs, Oct 2026](https://www.thundercompute.com/blog/best-open-source-llms)
- [LLM Stats: GPT-6.1 Sol and Opus 5.5 pricing](https://llm-stats.com/ai-news)
- [Pragmatic Engineer: When AI writes almost all code](https://newsletter.pragmaticengineer.com/p/when-ai-writes-almost-all-code-what)

### Pacing debate (September and October 2026)

- [Fello AI: what the labs actually committed to](https://felloai.com/ai-slowdown/)
- [Digital Applied: what was promised](https://www.digitalapplied.com/blog/ai-labs-pace-the-frontier-what-was-promised)
- [The Register: frontier AI keeps racing](https://www.theregister.com/ai-and-ml/2026/09/23/frontier-ai-keeps-racing-despite-calls-to-slow-down/5298448)
- [Shattered: pacing pledge vs a 5-model launch streak](https://shattered.io/amodei-pacing-pledge-5-model-launch-streak-2026/)
- [PYMNTS: slowdown won't slow adoption](https://www.pymnts.com/news/artificial-intelligence/2026/experts-say-frontier-ai-slowdown-wont-slow-adoption)
- [ibl.ai: model-agnostic infrastructure](https://ibl.ai/blog/frontier-labs-pace-the-frontier-model-agnostic-infrastructure)
- [CNN: OpenAI won't release GPT-6.1 Astra](https://www.cnn.com/2026/09/28/business/openai-chatgpt-safety-concerns)
- [CNBC: OpenAI DevDay 2026 recap](https://www.cnbc.com/2026/09/29/openai-devday-2026-live-updates.html)

### Earlier 2026

- [CEPR: Writing code versus shipping code](https://cepr.org/voxeu/columns/writing-code-versus-shipping-code-productivity-effects-across-generations-ai-coding)
- [DX: AI productivity gains more modest than expected](https://getdx.com/blog/ai-productivity-gains-more-modest-than-expected/)
- [Sonar: State of Code 2026](https://www.sonarsource.com/blog/state-of-code-developer-survey-report-the-current-reality-of-ai-coding/)
- [LinearB: the AI engineering productivity gap](https://linearb.io/resources/ai-engineering-productivity-gap)
- [Artificial Analysis: Kimi K3 at #3](https://artificialanalysis.ai/articles/kimi-k3-achieves-3-in-the-artificial-analysis-intelligence-index-comparable-to-opus-4-8-and-gpt-5-5)
- [Betanews: Opus 5.5 launch and price cut](https://betanews.com/article/claude-opus-5-5-launch-price-cut/)
- [People Matters: Microsoft Claude Code licences](https://www.peoplematters.in/amp/news/ai-and-emerging-tech/microsoft-cancels-claude-code-licences-after-engineers-use-it-too-much-49918)
- [Deloitte: agentic AI strategy](https://www.deloitte.com/us/en/insights/topics/technology-management/tech-trends/2026/agentic-ai-strategy.html)
- [Forbes: why most agentic AI pilots never ship](https://www.forbes.com/councils/forbestechcouncil/2026/09/14/why-most-agentic-ai-pilots-never-make-it-into-revenue-workflows/)
- [byteiota: AI code verification crisis (DORA 2024)](https://byteiota.com/ai-code-verification-crisis-96-distrust-48-verify/)
- [GMI Cloud: open-weight catches the frontier](https://www.gmicloud.ai/en/blog/ai-model-benchmarks-august-2026-open-weight-models-catch-the-frontier)
- [OpenHands: open models closing the coding gap](https://www.openhands.dev/blog/open-models-are-closing-the-coding-gap)
- [Digital Applied: open-weight release ledger](https://www.digitalapplied.com/blog/open-weight-announcement-to-weights-gap-ledger)
- [Bessemer: AI-pilled engineering teams](https://www.bvp.com/atlas/inside-ai-pilled-engineering-teams-five-lessons-for-scaling-without-losing-the-plot)
- [Cockroach Labs: agentic AI costs at scale (Uber)](https://www.cockroachlabs.com/blog/agentic-ai-costs-at-scale/)

### Our own daily episodes

- Jun 29 (GPT-5.6 limited preview), Sep 2 (Fable 5.1, Mythos 5.1, Astra Critical tier), Sep 4 (Astra phased release), Sep 17 (37,000 agent pull requests, GitHub Rust migration), Sep 18 (Google ECO), Sep 24 (agent pull request follow-up fixes), Sep 29 (Sonnet 5.5), Sep 30 (GPT-6.1 Sol, Dots), Oct 1 (Gemini 4 Argon), Oct 2 (coding agent session dataset)
