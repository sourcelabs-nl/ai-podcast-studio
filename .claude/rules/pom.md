---
paths:
  - "pom.xml"
---

# Dependency version rules

The versions here are the single source of truth: `spring-boot-starter-parent`,
`kotlin.version`, `spring-ai.version`. Nothing else in the repository restates
them, and nothing should start to.

**Before bumping a core version**, read the release notes for the version being
moved *to*, and check whether code that depends on it needs to change in the same
commit. Spring AI in particular has renamed and relocated APIs across majors.

- [Spring Boot release notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes) (swap `4.1` in the URL for the target version)
- [Spring Boot dependency versions](https://docs.spring.io/spring-boot/appendix/dependency-versions/index.html): what the parent manages, so a version is not pinned twice
- [Kotlin What's New](https://kotlinlang.org/docs/whatsnew24.html) (swap `24` for the target version) and [releases](https://kotlinlang.org/docs/releases.html)
- [Spring AI reference](https://docs.spring.io/spring-ai/reference/): serves the current major; the per-API links live in the `spring-ai` skill

After a bump the project must still compile and `mvn test` must pass before the
change is considered complete.
