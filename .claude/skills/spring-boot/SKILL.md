---
name: spring-boot
description: Spring Boot framework rules for transaction boundaries, bean lifecycle, Jackson configuration, and exception design. Applied to all .kt files during code review.
user_invocable: false
---

# Spring Boot Rules

8 rules for Spring Boot framework concerns. Applied to all `.kt` files.

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
class StoreService(
    private val storeRepository: StoreRepository,
    private val supplierRepository: SupplierRepository,
) {
    // Good: multiple writes wrapped in a transaction
    @Transactional
    fun createStore(store: Store): Store {
        val savedStore = storeRepository.save(store.toEntity())
        supplierRepository.save(store.supplier.toEntity(savedStore.id!!))
        return savedStore.toDomain()
    }

    // Good: single write, no @Transactional needed
    fun deleteStore(id: Long) {
        storeRepository.deleteById(id)
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

**Violation:**
```kotlin
interface OrderRepository : CrudRepository<OrderEntity, Long> {
    @Transactional  // Wrong: don't put @Transactional on repositories
    @Modifying
    @Query("DELETE FROM orders WHERE id = :id")
    fun deleteById(@Param("id") id: Long)
}
```

**Correct:**
```kotlin
@Service
class OrderService(private val repository: OrderRepository) {
    @Transactional
    fun deleteOrder(id: Long) {
        repository.deleteById(id)
    }
}
```

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
class OrderService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
) {
    // Good: @Transactional is required here because createOrder() is called
    // via self-invocation (this.createOrder()), bypassing Spring's proxy.
    @Transactional
    fun createOrderWithItems(store: Store, items: List<Product>): Order {
        val order = createOrder(store)  // self-invocation: inner @Transactional is ignored
        items.forEach { orderItemRepository.save(it.toEntity(order.id!!)) }
        return order
    }

    @Transactional
    fun createOrder(store: Store): Order {
        return orderRepository.save(Order(storeId = store.id!!).toEntity()).toDomain()
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

---

## Rule SB8: Translate Exceptions to HTTP in `@ControllerAdvice`, Not Inline

Mapping an exception (or a domain failure) to an HTTP status and error body is a cross-cutting web concern. It belongs in a `@RestControllerAdvice` / `@ControllerAdvice` exception handler, not in per-endpoint `try/catch` blocks inside controllers and not in response-body builder/mapper functions invoked from controllers. Centralizing it keeps controllers focused on validate-delegate-map, removes duplicated catch chains across endpoints, and gives one place per exception type that decides its status and payload. Pairs with Rule SB5 (exceptions stay domain-oriented; the advice owns the HTTP mapping) and architecture Rule A8.

Scope the advice to the relevant controllers with `@RestControllerAdvice(assignableTypes = [...])` when the same exception type must map differently in different areas, rather than sniffing exception messages to disambiguate.

**Violations to flag:**
- A controller catching a custom/domain exception and building a `ResponseEntity` with a hand-rolled error body, when a `@ControllerAdvice` handler would centralize it
- The same exception type caught with near-identical mapping in two or more controller methods (duplicated catch chains)
- A mapper/extension function whose only job is to turn an exception into a response body, called from a controller catch block (move the logic into the advice handler)
- Message-sniffing (`if (e.message.contains(...))`) to pick a status code instead of distinct exception types or a scoped advice

**Correct pattern:**
```kotlin
@RestControllerAdvice(assignableTypes = [OrderController::class])
class OrderExceptionHandler {
    @ExceptionHandler(WarehouseQuotaExceededException::class)
    fun handleQuotaExceeded(e: WarehouseQuotaExceededException): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
            mapOf("error" to e.message, "code" to "quota_exceeded", /* plan fields */)
        )
}

// Controller just delegates — no quota try/catch:
@PostMapping("/ship/{target}")
fun ship(...): ResponseEntity<Any> {
    val shipment = shippingService.ship(order, store, userId, target)
    return ResponseEntity.ok(shipment.toResponse())
}
```