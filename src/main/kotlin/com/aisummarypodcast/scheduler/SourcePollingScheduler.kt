package com.aisummarypodcast.scheduler

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.podcast.PodcastService
import com.aisummarypodcast.source.SourcePoller
import com.aisummarypodcast.source.SourceService
import com.aisummarypodcast.store.SourceRepository
import com.aisummarypodcast.store.SourceType
import jakarta.annotation.PreDestroy
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
import kotlinx.coroutines.supervisorScope
import java.net.URI
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.min
import kotlin.random.Random

@Component
class SourcePollingScheduler(
    private val sourcePoller: SourcePoller,
    private val sourceRepository: SourceRepository,
    private val sourceService: SourceService,
    private val appProperties: AppProperties,
    private val podcastService: PodcastService,
    private val pollDelayResolver: PollDelayResolver
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var firstCycle = true

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

        val allSources = sourceRepository.findAll().filter { it.enabled }
        log.info("[Polling] Checking {} enabled sources", allSources.count())

        val effectiveSources = if (firstCycle) {
            firstCycle = false
            applyStartupJitter(allSources)
        } else {
            allSources
        }

        val dueSources = effectiveSources.filter { isDue(it) }
        log.info("[Polling] {} sources are due for polling", dueSources.size)

        val sourcesByPodcast = allSources.groupBy { it.podcastId }

        val hostGroups = dueSources.groupBy { extractHost(it.url) }

        supervisorScope {
            hostGroups.map { (host, sources) ->
                async {
                    pollHostGroup(host, sources, sourcesByPodcast)
                }
            }.forEach { deferred ->
                try {
                    deferred.await()
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
        val sources = sourceRepository.findByPodcastId(podcastId).filter { it.enabled }
        if (sources.isEmpty()) {
            log.info("[Polling] Catch-up poll requested for podcast {} but it has no enabled sources", podcastId)
            return
        }
        log.info("[Polling] Catch-up poll of {} sources for podcast {}", sources.size, podcastId)
        val sourcesByPodcast = mapOf(podcastId to sources)
        val hostGroups = sources.groupBy { extractHost(it.url) }
        supervisorScope {
            hostGroups.map { (host, grouped) ->
                async { pollHostGroup(host, grouped, sourcesByPodcast) }
            }.forEach { deferred ->
                try {
                    deferred.await()
                } catch (e: Exception) {
                    log.error("[Polling] Catch-up host group failed for podcast {}", podcastId, e)
                }
            }
        }
    }

    private suspend fun pollHostGroup(host: String?, sources: List<Source>, sourcesByPodcast: Map<String, List<Source>>) {
        for ((index, source) in sources.withIndex()) {
            try {
                val podcast = podcastService.findById(source.podcastId)
                val userId = if (source.type == SourceType.TWITTER) podcast?.userId else null
                val maxArticleAgeDays = podcast?.maxArticleAgeDays ?: appProperties.source.maxArticleAgeDays
                val siblingSourceIds = sourcesByPodcast[source.podcastId]?.map { it.id } ?: listOf(source.id)
                sourcePoller.poll(source, userId, maxArticleAgeDays, siblingSourceIds)
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
    }

    internal suspend fun applyStartupJitter(sources: Iterable<Source>): List<Source> {
        val now = Instant.now()
        return sources.map { source ->
            if (source.lastPolled != null) return@map source

            val jitterMinutes = Random.nextInt(0, source.pollIntervalMinutes + 1)
            val syntheticLastPolled = now.minus(jitterMinutes.toLong(), ChronoUnit.MINUTES)
            val updated = source.copy(lastPolled = syntheticLastPolled.toString())
            sourceRepository.save(updated)
            log.info("[Polling] Applied startup jitter to source {}: synthetic lastPolled = {} ({} min ago)",
                source.id, syntheticLastPolled, jitterMinutes)
            updated
        }
    }

    private fun isDue(source: Source): Boolean {
        val lastPolled = source.lastPolled?.let { Instant.parse(it) } ?: return true
        val effectiveInterval = effectivePollIntervalMinutes(source)
        return lastPolled.plus(effectiveInterval, ChronoUnit.MINUTES).isBefore(Instant.now())
    }

    internal fun effectivePollIntervalMinutes(source: Source): Long {
        if (source.consecutiveFailures == 0) return source.pollIntervalMinutes.toLong()
        val maxBackoffMinutes = (source.maxBackoffHours ?: appProperties.source.maxBackoffHours).toLong() * 60
        val backoff = source.pollIntervalMinutes.toLong() * (1L shl min(source.consecutiveFailures, 30))
        return min(backoff, maxBackoffMinutes)
    }

    private fun extractHost(url: String): String? =
        try {
            URI(url).host
        } catch (_: Exception) {
            null
        }

    private fun cleanupOldArticles() {
        sourceService.cleanupOldArticlesAndPosts(appProperties.source.maxArticleAgeDays)
    }
}
