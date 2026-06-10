---
paths:
  - "src/main/kotlin/**/*Controller.kt"
---

# Controller authoring rules

Controllers validate input, delegate to services, and map responses. No business logic.

**Do**
- Validate input (null/format checks, return 400) and authorization (ownership, 403/404).
- Call a single service method and map its result with a `.toResponse()` extension from `{Domain}Mappers.kt`.
- Return correct status codes (201 create, 202 async, 409 conflict).

**Don't**
- Orchestrate multiple service calls with conditional logic between them.
- Call repositories for mutations (save/delete). Simple read lookups are tolerated, mutations are not.
- Compute or derive domain values, or duplicate logic a service already has.
- Define DTOs, mappers, enums, or helper functions in the controller file. Put them in `{Domain}Dtos.kt`, `{Domain}Mappers.kt`, `{Domain}Types.kt`.

Full rules: `architecture` skill (A1, A2, A7). On a violation here, also expect the `code-review` skill to flag it.
