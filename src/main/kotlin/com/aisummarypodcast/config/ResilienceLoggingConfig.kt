package com.aisummarypodcast.config

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.core.registry.EntryAddedEvent
import io.github.resilience4j.core.registry.EntryRemovedEvent
import io.github.resilience4j.core.registry.EntryReplacedEvent
import io.github.resilience4j.core.registry.RegistryEventConsumer
import io.github.resilience4j.retry.Retry
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Attaches logging to every circuit breaker and retry as it is created.
 *
 * Resilience4j's own state lives in its registry and surfaces through actuator, but the poll and LLM
 * stages are read day to day from `app.log`. Registering here rather than at each call site means a
 * breaker created on demand (one per source host) is instrumented exactly once, however many
 * sources share it.
 */
@Configuration
class ResilienceLoggingConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun circuitBreakerLogging(): RegistryEventConsumer<CircuitBreaker> =
        object : RegistryEventConsumer<CircuitBreaker> {
            override fun onEntryAddedEvent(event: EntryAddedEvent<CircuitBreaker>) {
                val breaker = event.addedEntry
                breaker.eventPublisher.onStateTransition {
                    val to = it.stateTransition.toState
                    // Opening is the noteworthy event; recovering back through half-open to closed
                    // is good news and should not read as a warning in app.log.
                    val message = "[Resilience] Circuit breaker '{}' {} -> {}"
                    if (to == CircuitBreaker.State.OPEN) {
                        log.warn(message, breaker.name, it.stateTransition.fromState, to)
                    } else {
                        log.info(message, breaker.name, it.stateTransition.fromState, to)
                    }
                }
            }

            override fun onEntryRemovedEvent(event: EntryRemovedEvent<CircuitBreaker>) = Unit
            override fun onEntryReplacedEvent(event: EntryReplacedEvent<CircuitBreaker>) = Unit
        }

    @Bean
    fun retryLogging(): RegistryEventConsumer<Retry> =
        object : RegistryEventConsumer<Retry> {
            override fun onEntryAddedEvent(event: EntryAddedEvent<Retry>) {
                val retry = event.addedEntry
                retry.eventPublisher.onRetry {
                    log.warn("[Resilience] Retry '{}' attempt {}: {}",
                        retry.name, it.numberOfRetryAttempts, it.lastThrowable?.message)
                }
            }

            override fun onEntryRemovedEvent(event: EntryRemovedEvent<Retry>) = Unit
            override fun onEntryReplacedEvent(event: EntryReplacedEvent<Retry>) = Unit
        }
}
