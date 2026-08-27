package com.aisummarypodcast.source

import com.aisummarypodcast.store.Source
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** Name of the Resilience4j config in `application.yaml` that all source-host breakers share. */
private const val HOST_BREAKER_CONFIG = "source-host"

/**
 * Host-level circuit breaker for source polling, backed by Resilience4j.
 *
 * A single dead host can sit behind dozens of sources (a decommissioned Nitter instance took out 36
 * at once), and per-source backoff handles each in isolation: every source independently retries,
 * independently backs off, and logs its own failure, so nothing ever says "this host is gone". One
 * breaker per host supplies that aggregate signal.
 *
 * Because all of a host's sources share one breaker, the sliding window fills within a single poll
 * round rather than over days. Only *permanent* failures (403/404/410/401/DNS) count: a host
 * answering 403 everywhere is gone, whereas one timing out everywhere is merely having a bad
 * afternoon, which per-source exponential backoff already covers. Transient failures are configured
 * as ignored, so they count as neither success nor failure.
 *
 * Recovery is Resilience4j's half-open state: after `waitDurationInOpenState` exactly one permitted
 * call probes the host while the rest are still rejected, and the breaker closes or reopens on the
 * result. Breaker state lives in the registry, so it resets on restart; the cost is one wasted poll
 * round per restart, after which the window refills and the breaker reopens.
 */
@Component
class SourceHostBreaker(private val circuitBreakerRegistry: CircuitBreakerRegistry) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Runs [poll] for [source] under its host's circuit breaker, returning whether it actually ran.
     *
     * Sources whose URL has no parseable host get no breaker and always poll: a breaker keyed on
     * null would lump unrelated sources together.
     */
    fun pollThroughBreaker(source: Source, poll: () -> PollFailure?): HostPollOutcome {
        val host = extractSourceHost(source.url) ?: return HostPollOutcome.Polled(poll())
        val breaker = breakerFor(host)

        return try {
            breaker.executeCallable {
                poll()?.let { throw PollFailureException.of(it) }
                HostPollOutcome.Polled(null)
            }
        } catch (e: CallNotPermittedException) {
            log.debug("[Polling] Breaker open for host {} — skipping {}", host, source.url)
            HostPollOutcome.Skipped
        } catch (e: PollFailureException) {
            HostPollOutcome.Polled(e.failure)
        }
    }

    /** True when [host]'s breaker is rejecting calls, i.e. the host looks structurally down. */
    fun isOpen(host: String): Boolean =
        breakerFor(host).state == CircuitBreaker.State.OPEN

    /**
     * True when [host]'s breaker is not currently closed, meaning a successful poll represents a
     * recovery rather than business as usual.
     */
    fun isTripped(host: String): Boolean =
        breakerFor(host).state != CircuitBreaker.State.CLOSED

    private fun breakerFor(host: String): CircuitBreaker =
        circuitBreakerRegistry.circuitBreaker(host, HOST_BREAKER_CONFIG)
}
