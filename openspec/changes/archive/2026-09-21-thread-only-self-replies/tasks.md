## 1. Thread only self-replies

- [x] 1.1 Parse the reply target from the normalised `R to @<target>:` title prefix
- [x] 1.2 Pass the group's author key into `groupPostsByThread` and attach a reply only when the target matches it
- [x] 1.3 Keep author-blind attachment when the author key is null
- [x] 1.4 Strip a leading `@` from the author-field fallback in `resolveAuthorKey`
- [x] 1.5 Cover a cross-account reply and a case-insensitive self-reply in `SourceAggregatorTest`
- [x] 1.6 Record the Narro feed's shape and the sampled distribution in `knowledge/`
