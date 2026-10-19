package com.aisummarypodcast.scheduler

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.podcast.EpisodeWindow
import com.aisummarypodcast.podcast.EpisodeWindowResolver
import com.aisummarypodcast.podcast.PodcastService
import com.aisummarypodcast.source.SourceService
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.Source
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.time.TimeSource

@Component
class BriefingGenerationScheduler(
    private val podcastService: PodcastService,
    private val sourcePollingScheduler: SourcePollingScheduler,
    private val sourceService: SourceService,
    private val episodeWindowResolver: EpisodeWindowResolver,
    private val appProperties: AppProperties,
    private val clock: Clock = Clock.systemUTC()
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        scope.launch {
            while (isActive) {
                try {
                    checkAndGenerate()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("[Pipeline] Unexpected error in briefing generation loop", e)
                }
                delay(60_000)
            }
        }
    }

    @PreDestroy
    fun stop() {
        scope.cancel()
    }

    suspend fun checkAndGenerate() {
        val podcasts = podcastService.findAll()

        for (podcast in podcasts) {
            try {
                val zone = episodeWindowResolver.zoneOf(podcast)
                val now = LocalDateTime.now(clock.withZone(zone))
                val cronExpression = CronExpression.parse(podcast.cron)
                val today: LocalDate = now.toLocalDate()

                // The next slot follows the last one an episode served. lastGeneratedAt only stands in
                // for a podcast whose episodes predate the window columns, and start of day for a new one.
                val lastServed = podcastService.findLatestServedWindowEnd(podcast, clock.instant())
                    ?: podcast.lastGeneratedAt?.let { Instant.parse(it) }
                val startFrom = lastServed?.let { LocalDateTime.ofInstant(it, zone) } ?: today.atStartOfDay()

                var nextExecution = cronExpression.next(startFrom)

                // Skip triggers from previous days
                while (nextExecution != null && !nextExecution.isAfter(now)
                    && nextExecution.toLocalDate().isBefore(today)
                ) {
                    log.warn("[Pipeline] Skipping previous-day trigger at {} for podcast '{}' ({})", nextExecution, podcast.name, podcast.id)
                    nextExecution = cronExpression.next(nextExecution)
                }

                if (nextExecution != null && !nextExecution.isAfter(now)) {
                    // The window ends at the slot being served, not at "now": a run that starts late
                    // still covers the period the slot stands for.
                    val slot = nextExecution.atZone(zone).toInstant()
                    if (!isRetryAllowed(podcast, slot)) continue

                    log.info("[Pipeline] Podcast '{}' ({}) is due for briefing generation", podcast.name, podcast.id)
                    val window = episodeWindowResolver.resolve(podcast, slot)

                    ensureFreshPolling(podcast)
                    catchUpBehindSources(podcast, window, slot)
                    if (isWindowCovered(podcast, window, slot)) {
                        generateBriefing(podcast, window)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("[Pipeline] Error checking/generating briefing for podcast '{}' ({}): {}", podcast.name, podcast.id, e.message, e)
            }
        }
    }

    /**
     * Whether [slot] may be generated given its earlier failures: a slot is retried once, as a new
     * episode, no sooner than [RETRY_DELAY] after the failure, and never after a second failure, so
     * a structural error costs two runs instead of one per minute. A manual retry or re-run is not
     * limited by this. A failure recorded before `failed_at` existed counts as long enough ago.
     */
    private fun isRetryAllowed(podcast: Podcast, slot: Instant): Boolean {
        val failed = podcastService.findFailedForSlot(podcast, slot)
        if (failed.isEmpty()) return true
        if (failed.size >= MAX_FAILURES_PER_SLOT) {
            log.debug("[Pipeline] Slot {} of podcast '{}' ({}) failed {} times; not retrying it automatically",
                slot, podcast.name, podcast.id, failed.size)
            return false
        }
        val failedAt = failed.mapNotNull { it.failedAt?.let { at -> Instant.parse(at) } }.maxOrNull() ?: return true
        val retryAt = failedAt.plus(RETRY_DELAY)
        if (clock.instant().isBefore(retryAt)) {
            log.debug("[Pipeline] Slot {} of podcast '{}' ({}) failed at {}; retrying from {}",
                slot, podcast.name, podcast.id, failedAt, retryAt)
            return false
        }
        log.info("[Pipeline] Retrying failed slot {} of podcast '{}' ({}) once", slot, podcast.name, podcast.id)
        return true
    }

    /**
     * Runs a catch-up poll of the podcast's sources when polling has gone stale, so generation does
     * not compose against stale data after the machine was asleep/offline through the cron time.
     * Stale means: no poll round has completed in this process yet, or the last one is older than
     * [SourceProperties.staleRoundThresholdMinutes].
     */
    private suspend fun ensureFreshPolling(podcast: Podcast) {
        val threshold = Duration.ofMinutes(appProperties.source.staleRoundThresholdMinutes.toLong())
        val lastRound = sourcePollingScheduler.lastPollRoundCompletedAt
        val stale = lastRound == null || Duration.between(lastRound, clock.instant()) > threshold
        if (!stale) return

        val reason = if (lastRound == null) "no poll round has completed yet" else "last poll round was at $lastRound"
        log.info("[Pipeline] Polling is stale ({}) — running a catch-up poll before generating for podcast '{}' ({})",
            reason, podcast.name, podcast.id)
        try {
            sourcePollingScheduler.pollPodcastSourcesNow(podcast.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("[Pipeline] Catch-up poll failed for podcast '{}' ({}); generating with existing data: {}",
                podcast.name, podcast.id, e.message, e)
        }
    }

    /**
     * Polls the podcast's sources that are behind [window] right away, instead of letting generation
     * wait for their regular poll interval, so a slot can be generated in the same tick. Only healthy
     * sources are polled: one whose last poll failed keeps its backoff. Runs only before the coverage
     * deadline; a failure here is logged and the coverage check decides as before.
     */
    private suspend fun catchUpBehindSources(podcast: Podcast, window: EpisodeWindow, slot: Instant) {
        if (!clock.instant().isBefore(coverageDeadline(slot))) return
        val healthyBehind = sourceService.findSourcesBehindWindow(podcast.id, window.end)
            .filter { it.consecutiveFailures == 0 }
        if (healthyBehind.isEmpty()) return

        log.info("[Pipeline] Catching up {} source(s) behind window {} for podcast '{}' ({}): {}",
            healthyBehind.size, window, podcast.name, podcast.id, labelsOf(healthyBehind))
        try {
            sourcePollingScheduler.pollSourcesNow(podcast.id, healthyBehind)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("[Pipeline] Slot catch-up poll failed for podcast '{}' ({}): {}",
                podcast.name, podcast.id, e.message, e)
        }
    }

    private fun coverageDeadline(slot: Instant): Instant =
        slot.plus(appProperties.episode.pollCoverageDeadlineMinutes.toLong(), ChronoUnit.MINUTES)

    /**
     * Whether the pollers have caught up enough with [window] to compose against it.
     *
     * A window that just closed is only worth composing once every source has fetched what it
     * published inside it. When some are still behind, generation is deferred and the next tick of
     * the loop re-checks: nothing is created yet, so the slot stays due and no state is touched.
     * The deferral is bounded by [EpisodeProperties.pollCoverageDeadlineMinutes], after which the
     * episode is generated anyway and the sources still behind are named in the log, because an
     * incomplete episode beats no episode at all. Only sources that will be polled again before that
     * deadline hold generation back: a source in backoff cannot catch up by being waited for.
     */
    private fun isWindowCovered(podcast: Podcast, window: EpisodeWindow, slot: Instant): Boolean {
        val behind = sourceService.findSourcesBehindWindow(podcast.id, window.end)
        if (behind.isEmpty()) return true

        val deadline = coverageDeadline(slot)
        if (clock.instant().isBefore(deadline)) {
            // Only a source that will be polled again before the deadline is worth waiting for. One in
            // backoff after repeated failures will not, so waiting for it only delays the episode.
            val maxBackoffHours = appProperties.source.maxBackoffHours
            val worthWaitingFor = behind.filter { source ->
                source.nextPollAt(maxBackoffHours)?.isAfter(deadline) != true
            }
            if (worthWaitingFor.isNotEmpty()) {
                log.info(
                    "[Pipeline] Deferring generation for podcast '{}' ({}): {} source(s) have not polled the " +
                        "window {} yet ({}). Waiting until {}.",
                    podcast.name, podcast.id, worthWaitingFor.size, window, labelsOf(worthWaitingFor), deadline
                )
                return false
            }
            log.warn(
                "[Pipeline] Generating for podcast '{}' ({}) without waiting: {} source(s) are behind window {} " +
                    "but will not poll again before {}: {}. The episode may be missing their content.",
                podcast.name, podcast.id, behind.size, window, deadline, labelsOf(behind)
            )
            return true
        }

        log.warn(
            "[Pipeline] Generating for podcast '{}' ({}) with {} source(s) still behind window {} after " +
                "waiting until {}: {}. The episode may be missing their content.",
            podcast.name, podcast.id, behind.size, window, deadline, labelsOf(behind)
        )
        return true
    }

    private fun labelsOf(sources: List<Source>): String =
        sources.joinToString(", ") { it.label ?: it.url }

    private suspend fun generateBriefing(podcast: Podcast, window: EpisodeWindow) {
        log.info("[Pipeline] Starting briefing generation for podcast '{}' ({}) over window {}",
            podcast.name, podcast.id, window)
        val mark = TimeSource.Monotonic.markNow()

        val result = podcastService.generateBriefing(podcast, window)
        if (result.failed) {
            log.error("[Pipeline] Briefing generation failed for podcast '{}' ({}): {} — total {}", podcast.name, podcast.id, result.errorMessage, mark.elapsedNow())
            return
        }
        if (result.episode == null) {
            log.info("[Pipeline] No briefing generated for podcast '{}' ({}) — skipped or no articles ({})", podcast.name, podcast.id, mark.elapsedNow())
            return
        }

        log.info("[Pipeline] Briefing generation complete for podcast '{}' ({}): episode {} — total {}", podcast.name, podcast.id, result.episode.id, mark.elapsedNow())
    }
}

private const val MAX_FAILURES_PER_SLOT = 2
private val RETRY_DELAY: Duration = Duration.ofMinutes(5)
