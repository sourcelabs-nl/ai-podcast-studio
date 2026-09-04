package com.aisummarypodcast

import com.aisummarypodcast.source.PollFailureException
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import java.time.Duration

/**
 * A [RetryRegistry] for tests: same retry semantics as production but with no wait between
 * attempts, so a test covering retry behaviour does not spend seconds sleeping.
 *
 * [maxAttempts] of 1 disables retrying, which is what most tests want — they assert one call's
 * behaviour and would otherwise see the same mocked failure replayed.
 */
fun testRetryRegistry(maxAttempts: Int = 1): RetryRegistry =
    RetryRegistry.of(
        RetryConfig.custom<Any>()
            .maxAttempts(maxAttempts)
            .waitDuration(Duration.ofMillis(1))
            .build()
    )

/**
 * A [CircuitBreakerRegistry] carrying the same `source-host` config the application defines, so
 * tests exercise the real breaker semantics rather than a mock that restates them.
 */
fun testCircuitBreakerRegistry(): CircuitBreakerRegistry =
    CircuitBreakerRegistry.of(
        mapOf(
            "source-host" to CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(3)
                .minimumNumberOfCalls(3)
                .failureRateThreshold(100f)
                .waitDurationInOpenState(Duration.ofMinutes(10))
                .permittedNumberOfCallsInHalfOpenState(1)
                .recordExceptions(PollFailureException.Permanent::class.java)
                .ignoreExceptions(PollFailureException.Transient::class.java)
                .build()
        )
    )

/** Drives [host]'s breaker open by feeding it enough permanent failures to fill its window. */
fun CircuitBreakerRegistry.openBreakerFor(host: String) {
    val breaker = circuitBreaker(host, "source-host")
    repeat(3) {
        runCatching {
            breaker.executeCallable<Unit> {
                throw PollFailureException.Permanent(com.aisummarypodcast.source.PollFailure.Permanent("HTTP 403 Forbidden"))
            }
        }
    }
}

/**
 * A [RetryRegistry] carrying the same `compose` config the application defines: two attempts, and
 * only a transient provider fault retried. Tests use it so they exercise the real policy — in
 * particular that a speaker-tag failure is NOT retried here, because the advisor inside the call
 * has already made its own attempts.
 */
fun testComposeRetryRegistry(): RetryRegistry {
    // Registered as an INSTANCE, not a config: RetryRegistry.of(map) takes configurations, and
    // retry("compose") would then hand back the default policy — retrying every exception — which
    // is the opposite of what this registry exists to pin down.
    val registry = RetryRegistry.ofDefaults()
    registry.retry(
        "compose",
        RetryConfig.custom<Any>()
            .maxAttempts(2)
            .waitDuration(Duration.ofMillis(1))
            .retryExceptions(
                com.openai.errors.OpenAIInvalidDataException::class.java,
                org.springframework.web.client.ResourceAccessException::class.java,
            )
            .build()
    )
    return registry
}
