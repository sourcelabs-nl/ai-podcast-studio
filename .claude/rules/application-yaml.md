---
paths:
  - "src/main/resources/application.yaml"
---

# application.yaml authoring rules

**Model pricing** (`input-cost-per-mtok`, `output-cost-per-mtok`, etc.): never guess or use training-data values. Verify on the provider's pricing page (e.g. `https://openrouter.ai/{provider}/{model}/pricing`) before setting or changing a number.

**Jackson**: configure features under `spring.jackson.*` (e.g. `spring.jackson.deserialization.fail-on-unknown-properties`), not by constructing `ObjectMapper`/`JsonMapper` in code. Components needing a mapper inject the Spring-managed `JsonMapper` bean.

**Defaults**: when adding config backed by an `@ConfigurationProperties` data class, keep the YAML defaults in sync with the data-class defaults and any Flyway-seeded values.

Full rules: `spring-boot` skill (SB6).
