---
okf_version: "0.2"
type: reference
title: Mutation testing for AI-written tests, and what it would take on this Kotlin codebase
answers: considering whether this project's tests catch real defects, or adding mutation testing
status: draft
generated:
  by: claude-opus-5-5
  at: 2026-09-23T00:00:00Z
source: >
  "Mutation Testing for AI-Generated Code", Augment Code guide,
  https://www.augmentcode.com/guides/mutation-testing-ai-generated-code, read
  2026-09-23. Tooling state from web search the same day; not yet run here.
---

# Mutation testing for AI-written tests, and what it would take on this Kotlin codebase

## What the guide claims

AI-written tests reach high line coverage while killing few mutants. The study it
cites (MutGen, HumanEval-Java) measured a 53% mutation score for generated tests
without mutation feedback and 89.5% when the surviving mutants were fed back into
the generation prompt. The weaknesses it names are boundary values, assertions
anchored to what the model expects rather than what the code does, and assertion
roulette. It reports Google's Mutagenesis (about 17M mutants over 760k changes,
2M surfaced in review), Meta's generated privacy tests (73% accepted,
Oct-Dec 2024) and Atlassian working toward 80%+. The score is
killed / (total - equivalent). Its workflow: mutate only changed code, reuse prior
results, gate on a score set per code criticality, review survivors before merge,
and feed genuine survivors back into test generation. For Java the tool is PIT
(pitest).

## What transfers

The profile fits: roughly 1,800 tests, most of them AI-written, on logic where a
wrong boundary is invisible in coverage. `AttentionScoring`, `OpenRouterRouting`
and `RunConfig.resolve` are pure logic with exact thresholds, the kind of code
where a surviving boundary mutant points at a real gap. Feeding survivors back as
test prompts matches how tests are written here.

## What does not transfer as-is

- **Kotlin.** PIT mutates JVM bytecode, so it runs on Kotlin without a plugin, but
  it also mutates what the Kotlin compiler generates (null checks, data class
  `equals`/`hashCode`/`copy`, inline and lambda bodies). Those survivors are
  equivalent mutants and add noise, not signal. The maintained Kotlin-aware filter
  is arcmutate's `com.arcmutate:pitest-kotlin-plugin`, which is commercial (price
  unverified); the open-source `pitest-kotlin` is unmaintained.
- **Java 25** is not an obstacle: the pitest maintainer states on issue #1439 that
  PIT reads bytecode up to and including Java 26.
- **A whole-repo run.** With this many tests and this many data classes, a full run
  would be slow and dominated by compiler-generated mutants.

## The Kotlin tools, 2026-09

| Tool | Maven | Kotlin-aware | State |
|---|---|---|---|
| PIT (`pitest-maven`) | yes | no, mutates bytecode | Apache 2.0, active, the JVM standard |
| arcmutate `pitest-kotlin-plugin` | yes, on PIT | yes, filters compiler-generated mutants and adds Kotlin mutators (elvis, `when`) | commercial licence key, no public price, last release 2026-05 |
| `pitest/pitest-kotlin` | yes, on PIT | partial, no inline handling | README says not maintained, points to arcmutate |
| Kaputt | no, Gradle 9 only | yes, compiler plugin, Kotlin 2.4 | active, young |
| mutflow | no, Gradle only | yes, K2 IR plugin inside JUnit | very new, small adoption |
| Mutant-Kraken | no plugin, standalone CLI | yes, source AST | beta, research origin (Mutation 2024) |
| Major, Stryker | no | no | Major dormant, Stryker has no JVM implementation |

The Kotlin-native tools are all Gradle-bound or beta, so for a Maven build the
choice is PIT alone or PIT with arcmutate. MockK is no obstacle to either: PIT
runs the existing JUnit 5 suite and mutates the production bytecode. The licence
texts of Kaputt and mutflow were not read.

## What a first trial looks like

`org.pitest:pitest-maven:1.30.0` (released 2026-08-27) with
`org.pitest:pitest-junit5-plugin:1.2.3`, both read from Maven Central, no
arcmutate, `targetClasses`/`targetTests` limited to `AttentionScoring`,
`OpenRouterRouting` and `RunConfigTypes`, on a scratch branch. The outcome decides
the follow-up: survivors that are mostly genuine gaps justify arcmutate or an
incremental `scmMutationCoverage`/`withHistory` check on changed files; survivors
that are mostly compiler noise mean mutation testing is not worth its upkeep here
without the Kotlin plugin. Not run yet, so this entry stays `draft`.

Related: [[judged-baseline-2026-09]] measures script quality, not test quality;
the two are independent.
