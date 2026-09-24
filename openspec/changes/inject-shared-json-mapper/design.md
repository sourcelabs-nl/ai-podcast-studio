## Context

The two topic extractors were stateless `object`s, which cannot receive Spring beans. `RoleTagValidationAdvisor` is not a bean: the composers create one per call with the roles for that podcast.

## Goals / Non-Goals

**Goals:** every Jackson mapper in main sources is the Spring-managed `JsonMapper`; no change in parsing or stored JSON.

**Non-Goals:** changing the delimiter protocol, the fallback-on-parse-miss behaviour, or the database JSON format.

## Decisions

- **Extractors become `@Component` classes.** Log and delimiter constants move to a companion object; the mapper is a constructor parameter. Alternative considered: passing a mapper into `extract(...)` on each call. Rejected because every caller would then need the mapper instead of the extractor, spreading the dependency further.
- **`RoleTagValidationAdvisor` takes the extractor as a constructor argument**, placed before the defaulted `maxRetries` so existing named-argument calls keep working. The composers pass their injected extractor in.
- **Converters take the mapper as a constructor argument**, supplied by the `jdbcCustomConversions(dialect, jsonMapper)` bean method. The enum and boolean converters are unchanged.

## Risks / Trade-offs

- [Central Jackson settings now apply to stored JSON] → the only setting is a lenient read option; verified by reading podcasts with model overrides, subtopics and voice maps through the API after restart.
- [Bean creation order between the JSON mapper and JDBC conversions] → the app starts and the full suite passes.
