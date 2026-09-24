package com.aisummarypodcast.llm

import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** How long to wait before the first lookup, between lookups, and in total before giving up. */
data class GenerationStatsRetry(
    val initialDelay: Duration = 5.seconds,
    val interval: Duration = 10.seconds,
    val giveUpAfter: Duration = 3.minutes
)

/** Lookups run on few threads: they are cheap GETs, and a scoring fan-out must not start dozens at once. */
private const val LOOKUP_PARALLELISM = 4

/**
 * Fetches OpenRouter's account of a request once it is available and writes it onto the request's
 * record, so a slow request can be split into queueing, generating and provider fallbacks.
 *
 * This is observability: it runs after the request has returned, off the caller's thread, and a
 * lookup that keeps failing costs a measurement and a warning, never an episode. Pending lookups
 * live in memory and are dropped on shutdown, which leaves those records without stats; the
 * alternative, a sweeper over the table, would need each user's API key stored next to the
 * telemetry.
 */
@Service
class GenerationStatsService(
    private val client: OpenRouterGenerationClient,
    private val llmCallLogService: LlmCallLogService,
    private val retry: GenerationStatsRetry = GenerationStatsRetry()
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(LOOKUP_PARALLELISM) + SupervisorJob())

    @PreDestroy
    fun stop() {
        scope.cancel()
    }

    fun schedule(callId: Long, generationId: String, lookup: GenerationStatsLookup) {
        scope.launch { fetchAndRecord(callId, generationId, lookup) }
    }

    internal suspend fun fetchAndRecord(callId: Long, generationId: String, lookup: GenerationStatsLookup) {
        delay(retry.initialDelay)
        var waited = retry.initialDelay
        while (true) {
            val stats = try {
                client.fetch(generationId, lookup)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.debug("Generation stats lookup for {} failed, will retry: {}", generationId, e.message)
                null
            }
            if (stats != null) {
                llmCallLogService.recordGenerationStats(callId, stats)
                return
            }
            if (waited + retry.interval > retry.giveUpAfter) {
                log.warn("No generation stats for {} (LLM call {}) after {}; giving up", generationId, callId, waited)
                return
            }
            delay(retry.interval)
            waited += retry.interval
        }
    }
}

/**
 * The generation stats lookup for one chat model's requests: the service that runs lookups and the
 * credentials of the user whose calls they are. Built only for OpenRouter models.
 */
class GenerationStatsTracker(
    private val service: GenerationStatsService,
    private val lookup: GenerationStatsLookup
) {
    fun track(callId: Long, generationId: String) = service.schedule(callId, generationId, lookup)
}
