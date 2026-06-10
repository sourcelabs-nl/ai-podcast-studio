---
paths:
  - "src/test/**/*.kt"
---

# Test authoring rules

This project uses **MockK**, never Mockito.

**Do**
- Mock with MockK: `every { ... } returns ...`, `coEvery { ... }` for suspend functions, `verify { ... }`.
- For Spring integration tests, inject mocks with `@MockkBean` from `com.ninja-squad:springmockk`.

**Don't**
- Import anything from `org.mockito`.
- Use `@MockBean` (Spring's Mockito annotation) — use `@MockkBean`.
- Mix Mockito syntax (`when(...).thenReturn(...)`) into a MockK test.

Test code may construct standalone `JsonMapper` instances (tests don't always load the Spring context), so SB6 does not apply here.

Full rules: `kotlin-quality` skill (K2).
