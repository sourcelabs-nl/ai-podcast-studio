---
name: spring-boot
description: Spring Boot framework rules for transaction boundaries, bean lifecycle, Jackson configuration, and exception design. Applied to all .kt files during code review.
user_invocable: false
---

# Spring Boot Rules

7 rules for Spring Boot framework concerns. Applied to all `.kt` files.

---

## Rule SB1: `@Transactional` for Multi-Write Modifications

Service methods that perform multiple repository write operations (save, delete) must be annotated with `@Transactional`. Without it, each write runs in its own transaction and a failure partway through leaves the database in an inconsistent state.

Single-write operations do not need `@Transactional` because Spring Data JDBC wraps each repository call in its own transaction automatically.

**Violations to flag:**
- Service methods that perform multiple repository writes without `@Transactional`
- Service methods that read-then-write (e.g., find + save) without `@Transactional` when atomicity matters

**Correct pattern:**
```kotlin
@Service
class PodcastService(
    private val podcastRepository: PodcastRepository,
    private val sourceRepository: SourceRepository,
) {
    // Good: multiple writes wrapped in a transaction
    @Transactional
    fun createPodcast(podcast: Podcast): Podcast {
        val savedPodcast = podcastRepository.save(podcast.toEntity())
        sourceRepository.save(podcast.source.toEntity(savedPodcast.id!!))
        return savedPodcast.toDomain()
    }

    // Good: single write, no @Transactional needed
    fun deletePodcast(id: Long) {
        podcastRepository.deleteById(id)
    }
}
```

---

## Rule SB2: `@Transactional` Placement

`@Transactional` belongs on service methods, not on repositories or controllers.

- Repositories: Spring Data JDBC already wraps each call in a transaction. Adding `@Transactional` on a repository method is redundant and misleading.
- Controllers: Transaction boundaries are a service-layer concern. Controllers should not manage transactions.

**Violations to flag:**
- `@Transactional` on a repository interface method
- `@Transactional` on a controller method

---

## Rule SB3: No Unnecessary `@Transactional`

Do not add `@Transactional` to methods that don't need it. Unnecessary transactions add overhead and obscure intent.

**Violations to flag:**
- `@Transactional` on service methods that only read data (no writes)
- `@Transactional` on service methods that call a single repository write method (not a service method, see SB4)

**Do NOT flag:**
- `@Transactional` on methods that delegate to another `@Transactional` method on the same bean (see SB4)

---

## Rule SB4: Self-Invocation Bypasses Spring Proxy

Spring's `@Transactional` relies on AOP proxying. When a method on a bean calls another method on the **same bean** (self-invocation via `this`), the call bypasses the proxy entirely. This means `@Transactional` on the inner method has no effect.

If method A calls method B on the same service, and B needs a transaction, then A must also be annotated with `@Transactional` (or the call must go through the proxy).

**Violations to flag:**
- Removing `@Transactional` from a public method that delegates to a `@Transactional` method on the same bean, under the incorrect assumption that the inner annotation is sufficient

**Correct pattern:**
```kotlin
@Service
class EpisodeService(
    private val episodeRepository: EpisodeRepository,
    private val articleRepository: EpisodeArticleRepository,
) {
    // Good: @Transactional is required here because createEpisode() is called
    // via self-invocation (this.createEpisode()), bypassing Spring's proxy.
    @Transactional
    fun createEpisodeWithArticles(podcast: Podcast, articles: List<Article>): Episode {
        val episode = createEpisode(podcast)  // self-invocation: inner @Transactional is ignored
        articles.forEach { articleRepository.save(it.toEntity(episode.id!!)) }
        return episode
    }

    @Transactional
    fun createEpisode(podcast: Podcast): Episode {
        return episodeRepository.save(Episode(podcastId = podcast.id!!).toEntity()).toDomain()
    }
}
```

---

## Rule SB5: Exceptions Must Not Carry HTTP Status

Exception classes must not store `HttpStatus` as a field or constructor parameter. HTTP status codes are a web-layer concern; exceptions are domain concepts. The exception handler (`@ControllerAdvice`) is the single place that maps exception types to HTTP status codes.

Similarly, exception class names should not contain HTTP terminology (`BadRequest`, `NotFound`, `Conflict`, etc.). Use domain-oriented names instead.

**Violations to flag:**
- Exception classes with an `HttpStatus` constructor parameter or field
- Exception class names that reference HTTP concepts (e.g., `BadRequestException`, `NotFoundException`, `ConflictException`)
- Services throwing exceptions that carry HTTP status

---

## Rule SB6: Jackson Configuration via Spring Boot Properties and Injection

Jackson serialization/deserialization features must be configured via Spring Boot properties in `application.yaml`, not by constructing custom `ObjectMapper`/`JsonMapper` instances in application code. The Spring-managed `JsonMapper` bean picks up all configured properties automatically.

When a component needs a Jackson mapper (e.g., to pass to `BeanOutputConverter` for Spring AI LLM response parsing), inject the Spring-managed `JsonMapper` bean rather than creating one manually.

**Violations to flag:**
- Creating `JsonMapper.builder()...build()` or `ObjectMapper()` in application code (not test code) when features should come from Spring Boot properties
- Using `jacksonObjectMapper()` from jackson-module-kotlin in Spring-managed components instead of injecting the Spring `JsonMapper` bean
- Configuring Jackson features (e.g., `JsonReadFeature`, `DeserializationFeature`) programmatically in companion objects or init blocks instead of via `spring.jackson.*` properties

**Correct pattern:**
```yaml
# application.yaml
spring:
  jackson:
    json:
      read:
        allow-backslash-escaping-any-character: true
    deserialization:
      fail-on-unknown-properties: false
```

```kotlin
@Component
class MyLlmComponent(
    private val jsonMapper: JsonMapper  // Spring-managed, includes all configured features
) {
    fun callLlm() {
        // Pass the Spring-managed mapper to BeanOutputConverter
        val converter = BeanOutputConverter(MyResult::class.java, jsonMapper)
        val response = chatClient.prompt()
            .user(prompt)
            .call()
            .responseEntity(converter)
    }
}
```

**Note:** Test code may create standalone `JsonMapper` instances since tests don't always load the Spring context.

---

## Rule SB7: `@Value` Annotations Must Use Multi-Dollar Strings

Spring `@Value` annotations with property placeholders (`${...}`) must use Kotlin's multi-dollar string interpolation (`$$"..."`) instead of backslash escaping (`"\${...}"`). This is cleaner and avoids the need for escape characters.

**Violations to flag:**
- `@Value("\${...}")` with backslash-escaped dollar signs (use `$$"${...}"` instead)

**Correct pattern:**
```kotlin
@Service
class MyService(
    @Value($$"${app.feature.enabled:false}")
    private val featureEnabled: Boolean,
)
```