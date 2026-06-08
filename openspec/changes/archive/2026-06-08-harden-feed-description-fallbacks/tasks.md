## 1. Feed renderer hardening (retrofit: already implemented)

- [x] 1.1 Coalesce blank topic labels to "Other" in `FeedGenerator.buildHtmlDescription` and omit the "Topics Covered" section when no non-blank topic exists
- [x] 1.2 Add a shared `scriptFallback` helper that strips leaked preamble and speaker tags (keeping spoken text) and use it from both `buildPlainDescription` and `buildHtmlDescription`
- [x] 1.3 Add `FeedGeneratorTest` cases: all-blank topics omitted (no empty `<li>`), blank labeled "Other" alongside named topics, and script fallback strips tags + preamble
- [x] 1.4 Run `mvn test` and verify the full suite passes
