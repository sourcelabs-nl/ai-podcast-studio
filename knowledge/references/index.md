---
okf_version: "0.2"
type: index
title: External references
status: stable
generated:
  by: claude-opus-5
  at: 2026-09-15T00:00:00Z
---

# External references

Material from outside the project that shaped a decision here. Each entry states
what transfers and what does not, because a show that works for its own audience
is not automatically a model for this one. Third-party formats we read are
described here on the same terms: what the format guarantees, and what it leaves
to inference.

- [The Dutch two-host reference shows](dutch-two-host-shows.md)  
  Read when deciding whether to imitate something heard in the reference shows.
- [The reference shows have long turns, not short ones](turn-length-in-the-reference-shows.md)  
  Read when considering shorter turns to get closer to the reference shows.
- [The anchor story as a throughline](anchor-story-device.md)  
  Read when looking for an untried attention or structure device for the compose prompt.
- [What a Narro RSS feed carries, and what it does not](narro-feed-format.md)  
  Read when changing how threads are reconstructed from Narro-sourced X content.
- [Server-side web search, and why this project keeps Tavily](server-side-web-search-on-the-jvm.md)  
  Read when considering a move off Tavily to a provider's own web search.
- [What Jev can decide for this pipeline, and what it cannot](jev-decisions-endpoint.md)  
  Read when deciding where Jev can carry a call in this pipeline and where it cannot.
- [Jev as a script judge, measured against this project's own judge](jev-as-a-judge.md)  
  Read when considering Jev in place of, or alongside, ScriptJudge.
- [The SQLite driver returns no generated keys from a batch](generated-keys-from-a-batch.md)  
  Read when a save of several new rows fails with "After saving the identifier must not be null".
- [Mutation testing for AI-written tests, and what it would take on this Kotlin codebase](mutation-testing-ai-written-tests.md)  
  Read when considering whether this project's tests catch real defects, or adding mutation testing.
- [OpenAI reasoning models reject temperature, and OpenRouter reports it as a routing failure](openai-reasoning-models-reject-temperature.md)  
  Read when an openai/ model returns 404 "No endpoints found that can handle the requested parameters".
- [Spring AI 2.0.1 caps every OpenAI request at 60 seconds unless the call options carry a timeout](spring-ai-2-0-1-per-request-timeout.md)  
  Read when LLM requests fail at exactly 60s with "Error reading response", or when upgrading Spring AI.
