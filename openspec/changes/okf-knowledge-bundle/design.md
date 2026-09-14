## Context

Two sources shaped this, and they sit on different layers.

Open Knowledge Format v0.2 defines the file shape. It is explicit that a corpus is "continuously written and maintained by agents", and its whole v0.2 feature set exists to answer what a reader of a machine-written corpus needs: what was this made from, how much should I trust it, is it still true, is it current. It deliberately specifies no write loop, no retrieval mechanism, and no type taxonomy.

The LLM-wiki pattern supplies exactly what OKF leaves out: three layers (immutable raw sources, an agent-owned wiki, and a schema document that configures the agent), and three operations. Its central claim is that the maintenance burden, not the thinking, is what kills a knowledge base, and that this burden is near zero for a model.

What neither supplies is a fit to our situation, because both assume knowledge arrives as documents to ingest. Ours does not. It arrives as measurements we run and experiments we choose to perform.

## Decisions

**OKF supplies the frontmatter; the wiki pattern supplies the loop.**

Neither is adopted whole. From OKF: the directory shape, `index.md`, `log.md`, and the provenance fields. From the wiki pattern: the three layers, record/query/lint, and the schema document as the thing that makes any of it happen. Where they overlap they agree, which is some evidence both are right: both arrived independently at a content-oriented index for progressive disclosure and a chronological log.

**Ingest becomes record.**

The wiki pattern's primary operation is reading an external document and integrating it. That is our weakest case. Our raw layer is the episode archive, probe output and score rows, and knowledge enters when we measure something or run an ablation, not when we read something. So the operation is renamed and reshaped around producing an entry from an experiment.

The corollary is that the pattern's secondary operation carries more weight for us than for it: an answer worth keeping gets filed back as an entry. An ablation showing no difference is exactly such an answer, and is exactly the kind that evaporates into a transcript.

**Trust lives in frontmatter, not in file location.**

The first draft of this change split knowledge by who wrote it, putting agent-written notes somewhere separate. That was wrong. OKF's `generated` is not an AI flag: it is an actor-and-time record present on every entry, `human:soudmaijer` and a model identifier using the same field. `verified` is a separate list of confirmation events, on the stated reasoning that who wrote a concept need not be who confirmed it.

That yields three legible tiers from the fields alone: no `verified` means nobody has checked it, a machine confirmation means it was re-derived, and a `human:` confirmation means a person looked. The pace measurement and an inference drawn from reading a prompt can then sit in the same directory without being confusable, which location-based separation never achieves.

**`status` is lifecycle, `verified` is hardness.**

These were conflated in the first draft, which made `draft` the default and used it to mean "weakly established". OKF defaults `status` to `stable` when absent and intends the field for lifecycle: draft, current, deprecated. How well a claim is established is `verified`'s job. Keeping them separate means a deprecated entry can still be a well-verified one, which is the normal case for a rule we removed on evidence.

**Types are open, with a project convention on top.**

OKF states that type values are not registered centrally, that consumers must tolerate unknown types, and lists a fixed taxonomy as an explicit non-goal. The first draft fixed four types and called a fifth an error, which is stricter than the format allows. The four remain as a convention because an unconstrained type field goes shapeless within a dozen entries, but a new type is a judgement call in review rather than a violation.

**`log.md` is used, with a parseable prefix.**

The first draft banned it on the reasoning that an append-only stream is what the chat transcript already is. Both sources contradict that, and the reasoning was wrong: the transcript is not queryable, not in git, and not scoped to a directory. OKF describes `log.md` as date-grouped, newest first, with conventional labels including deprecation, which is precisely the mechanism for keeping a removed rule's reasoning once its code is gone. The wiki pattern adds the detail that makes it cheap to use: a consistent line prefix, so `grep "^## \[" log.md | tail -5` answers what happened recently without reading the file.

The division with entry bodies is firm. An entry states what is currently known and is edited in place. The log says what changed and when. An entry that grows a history section is a lint finding.

**Lint is what makes staleness real.**

`stale_after` is an absolute instant, so staleness is a plain comparison. But nothing performs that comparison on its own. Without a scheduled pass, the field is decoration and an expired measurement keeps being read as current. Lint is therefore not optional polish; it is the only thing that discharges the risk the frontmatter was designed to express.

**`CLAUDE.md` is the configuration, and it is small.**

This is the answer to where any of this is configured. It carries the conventions and the operations, and points at `knowledge/index.md` rather than restating the bundle's contents, so what every session pays to load stays bounded. It is expected to change as we find out which parts we actually use.

## Deliberate divergence from OKF

OKF's stated goals are portability and exchange across organizations, and its distribution model is a bundle you hand off. We are using it as project-internal knowledge that ships with the repo. The write side is built for exactly this, so the divergence is safe, but it means the export-oriented parts carry no weight here: `sources` with credibility signals is aimed at a consumer weighing third-party material, and our sources are our own database and our own probes. Those fields are left out rather than filled in pro forma.

## Non-Goals

- Loading the bundle into the application at runtime. Nothing reads it but people and agents.
- A schema validator or CI check. Lint is a judgement pass, not a linter; a validator over four optional-field types would cost more than the drift it prevents.
- A search tool over the bundle. The index is sufficient well past the size this will reach; a retrieval layer is a later decision if it ever stops being.
- Migrating existing KDoc rationale out of the code. It is good where it is, and the bundle links to it.
