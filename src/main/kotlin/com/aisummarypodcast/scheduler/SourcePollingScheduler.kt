package com.aisummarypodcast.scheduler

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.podcast.PodcastService
import com.aisummarypodcast.source.HostPollOutcome
import com.aisummarypodcast.source.extractSourceHost
import com.aisummarypodcast.source.SourceHostBreaker
import com.aisummarypodcast.source.SourcePoller
import com.aisummarypodcast.source.SourceService
import com.aisummarypodcast.store.SourceType
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import com.aisummarypodcast.store.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

@Component
class SourcePollingScheduler(
    private val sourcePoller: SourcePoller,
    private val sourceService: SourceService,
    private val appProperties: AppProperties,
    private val podcastService: PodcastService,
    private val pollDelayResolver: PollDelayResolver,
    private val sourceHostBreaker: SourceHostBreaker
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var firstCycle = true

    /** One lock per source, so the polling loop and the catch-up polls never poll a source at the same time. */
    private val sourceLocks = ConcurrentHashMap<String, Mutex>()

    /**
     * Wall-clock time the most recent poll round finished, or null before any round has completed in
     * this process. Kept in memory on purpose: after a restart it is null, which signals "freshness
     * unknown" so callers can force a catch-up poll. Used to detect whether polling has been running
     * recently (versus the machine having been asleep/offline).
     */
    @Volatile
    final var lastPollRoundCompletedAt: Instant? = null
        private set

    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        scope.launch {
            while (isActive) {
                try {
                    pollSources()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("[Polling] Unexpected error in polling loop", e)
                }
                delay(60_000)
            }
        }
    }

    @PreDestroy
    fun stop() {
        scope.cancel()
    }

    suspend fun pollSources() {
        cleanupOldArticles()

        val allSources = sourceService.findEnabled()
        log.info("[Polling] Checking {} enabled sources", allSources.count())

        val effectiveSources = if (firstCycle) {
            firstCycle = false
            applyStartupJitter(allSources)
        } else {
            allSources
        }

        val dueSources = effectiveSources.filter { it.isDue(appProperties.source.maxBackoffHours) }
        log.info("[Polling] {} sources are due for polling", dueSources.size)

        val sourcesByPodcast = allSources.groupBy { it.podcastId }

        val hostGroups = dueSources.groupBy { extractSourceHost(it.url) }
        // Breaker state covers every enabled source of a host, not only those due this round: a host
        // is down or it is not, regardless of which of its sources happen to be scheduled now.
        val enabledByHost = allSources.groupBy { extractSourceHost(it.url) }

        supervisorScope {
            hostGroups.map { (host, sources) ->
                async {
                    pollHostGroup(host, sources, enabledByHost[host] ?: sources, sourcesByPodcast, catchUpRequestedAt = null)
                }
            }.forEach { deferred ->
                try {
                    deferred.await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("[Polling] Host group failed with unexpected error", e)
                }
            }
        }

        // Eagerly rank non-aggregate sources for podcasts polled this round, so their articles
        // appear scored on the upcoming page without waiting for generation. Run sequentially and
        // inline so two poll cycles cannot concurrently score the same unscored article.
        val polledPodcastIds = dueSources.map { it.podcastId }.distinct()
        for (podcastId in polledPodcastIds) {
            try {
                podcastService.findById(podcastId)?.let { podcastService.scoreReadySources(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("[Eager] Eager ranking failed for podcast {}", podcastId, e)
            }
        }

        lastPollRoundCompletedAt = Instant.now()
    }

    /**
     * Polls all enabled sources of a single podcast to completion, reusing the same host-grouped
     * polling and per-host delays as the scheduled loop. Used as a catch-up poll right before
     * generation when polling has gone stale (e.g. the machine was asleep through the cron time).
     */
    suspend fun pollPodcastSourcesNow(podcastId: String) {
        val sources = sourceService.findEnabledByPodcastId(podcastId)
        if (sources.isEmpty()) {
            log.info("[Polling] Catch-up poll requested for podcast {} but it has no enabled sources", podcastId)
            return
        }
        pollSourcesNow(podcastId, sources)
    }

    /**
     * Polls the given [sources] of one podcast right away, through the same host-grouped path as the
     * scheduled loop. A source that was polled at or after the moment of this request (for example by
     * the loop while this call waited for its lock) is not polled again.
     */
    suspend fun pollSourcesNow(podcastId: String, sources: List<Source>) {
        if (sources.isEmpty()) return
        val requestedAt = Instant.now()
        log.info("[Polling] Catch-up poll of {} sources for podcast {}", sources.size, podcastId)
        val sourcesByPodcast = mapOf(podcastId to sourceService.findEnabledByPodcastId(podcastId).ifEmpty { sources })
        val hostGroups = sources.groupBy { extractSourceHost(it.url) }
        supervisorScope {
            hostGroups.map { (host, grouped) ->
                async { pollHostGroup(host, grouped, grouped, sourcesByPodcast, catchUpRequestedAt = requestedAt) }
            }.forEach { deferred ->
                try {
                    deferred.await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("[Polling] Catch-up host group failed for podcast {}", podcastId, e)
                }
            }
        }
    }

    /**
     * Polls the due [sources] of one host, each through the host's circuit breaker.
     *
     * When the breaker is open every call is rejected without a request, except the single half-open
     * probe Resilience4j permits once `wait-duration-in-open-state` has elapsed. A dead host
     * therefore costs one request a day rather than one per source per round.
     *
     * A poll that succeeds while the breaker is not closed is a recovery, so the rest of the host's
     * failure state is cleared: without that the breaker would close while every other source still
     * sat on its own accumulated backoff, and a recovered host would trickle back over days.
     *
     * [allHostSources] is every enabled source of this host, which may be wider than [sources],
     * since recovery has to restore siblings that were not themselves due this round.
     *
     * Each source is polled under its own lock and re-read once the lock is held: [catchUpRequestedAt]
     * is set for a catch-up poll, which skips a source polled at or after that moment, and null for
     * the loop, which skips a source that is no longer due.
     */
    private suspend fun pollHostGroup(
        host: String?,
        sources: List<Source>,
        allHostSources: List<Source>,
        sourcesByPodcast: Map<String, List<Source>>,
        catchUpRequestedAt: Instant?
    ) {
        var skipped = 0

        for ((index, source) in sources.withIndex()) {
            try {
                sourceLocks.computeIfAbsent(source.id) { Mutex() }.withLock {
                    val current = sourceService.findById(source.id)
                    if (current == null || !needsPoll(current, catchUpRequestedAt)) {
                        log.debug("[Polling] Source {} was already polled; skipping", source.id)
                    } else if (pollSourceWithTimeout(host, current, allHostSources, sourcesByPodcast)) {
                        skipped++
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("[Polling] Unexpected error polling source {} in host group {}", source.id, host, e)
            }
            if (index < sources.size - 1) {
                val delaySeconds = pollDelayResolver.resolveDelaySeconds(source)
                if (delaySeconds > 0) {
                    delay(delaySeconds * 1000L)
                }
            }
        }

        // One line for the whole host, instead of a failure log per suppressed source.
        if (skipped > 0) {
            log.warn("[Polling] Host {} looks structurally down — skipped {} of {} due sources",
                host, skipped, sources.size)
        }
    }

    private fun needsPoll(source: Source, catchUpRequestedAt: Instant?): Boolean {
        if (catchUpRequestedAt == null) return source.isDue(appProperties.source.maxBackoffHours)
        val lastPolled = source.lastPolled?.let { Instant.parse(it) } ?: return true
        return lastPolled.isBefore(catchUpRequestedAt)
    }

    /**
     * [pollSource] bounded by `app.source.poll-timeout`: a poll still blocked after it is interrupted,
     * because every round waits for all of its host groups and one hanging poll would otherwise stop
     * polling altogether. Returns false on a timeout, so the source is retried once it is due again.
     */
    private suspend fun pollSourceWithTimeout(
        host: String?,
        source: Source,
        allHostSources: List<Source>,
        sourcesByPodcast: Map<String, List<Source>>
    ): Boolean {
        val timeout = appProperties.source.pollTimeout
        val skipped = withTimeoutOrNull(timeout.toMillis()) {
            runInterruptible { pollSource(host, source, allHostSources, sourcesByPodcast) }
        }
        if (skipped == null) {
            log.warn("[Polling] Poll of source {} ({}) did not finish within {}; interrupted it", source.id, source.url, timeout)
        }
        return skipped ?: false
    }

    /** Polls one source through the host breaker; returns true when the open breaker skipped it. */
    private fun pollSource(
        host: String?,
        source: Source,
        allHostSources: List<Source>,
        sourcesByPodcast: Map<String, List<Source>>
    ): Boolean {
        val podcast = podcastService.findById(source.podcastId)
        val userId = if (source.type == SourceType.TWITTER) podcast?.userId else null
        val maxArticleAgeDays = podcast?.maxArticleAgeDays ?: appProperties.source.maxArticleAgeDays
        val siblingSourceIds = sourcesByPodcast[source.podcastId]?.map { it.id } ?: listOf(source.id)

        val wasTripped = host != null && sourceHostBreaker.isTripped(host)
        val outcome = sourceHostBreaker.pollThroughBreaker(source) {
            sourcePoller.poll(source, userId, maxArticleAgeDays, siblingSourceIds)
        }

        return when (outcome) {
            is HostPollOutcome.Skipped -> true
            is HostPollOutcome.Polled -> {
                if (wasTripped && outcome.failure == null) {
                    log.info("[Polling] Host {} recovered — restoring {} sources to their normal interval",
                        host, allHostSources.size)
                    sourceService.resetFailureState(allHostSources.filter { it.id != source.id })
                }
                false
            }
        }
    }

    internal suspend fun applyStartupJitter(sources: Iterable<Source>): List<Source> {
        val now = Instant.now()
        return sources.map { source ->
            if (source.lastPolled != null) return@map source

            val jitterMinutes = Random.nextInt(0, source.pollIntervalMinutes + 1)
            val syntheticLastPolled = now.minus(jitterMinutes.toLong(), ChronoUnit.MINUTES)
            val updated = sourceService.applyStartupJitter(source, syntheticLastPolled)
            log.info("[Polling] Applied startup jitter to source {}: synthetic lastPolled = {} ({} min ago)",
                source.id, syntheticLastPolled, jitterMinutes)
            updated
        }
    }

    private fun cleanupOldArticles() {
        sourceService.cleanupOldArticlesAndPosts(appProperties.source.maxArticleAgeDays)
    }
}
