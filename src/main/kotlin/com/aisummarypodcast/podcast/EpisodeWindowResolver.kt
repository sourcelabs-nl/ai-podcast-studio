package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeRepository
import com.aisummarypodcast.store.Podcast
import org.slf4j.LoggerFactory
import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Computes the article window an episode covers.
 *
 * The window runs from the podcast's previous scheduled slot up to the slot being served, so a
 * daily weekday cron yields 24 hours on Tuesday through Friday and reaches back across the weekend
 * on Monday, without weekdays being hardcoded anywhere: whatever the cron says its previous slot is
 * becomes the start.
 *
 * Two bounds apply on top of that:
 *
 * - **A gap is healed.** If the last episode that actually covered a window ended before the
 *   previous slot, the start moves back to where that episode ended, so the content of a day whose
 *   episode failed or was discarded is not orphaned between two windows.
 * - **The reach is capped** at [EpisodeProperties.maxWindowDays]. A podcast that has not generated
 *   for weeks pulls in one capped window rather than a month of articles at once.
 */
@Service
class EpisodeWindowResolver(
    private val episodeRepository: EpisodeRepository,
    private val appProperties: AppProperties,
    private val clock: Clock = Clock.systemUTC()
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** The window for a run happening now, used by a manual generate. */
    fun resolveForNow(podcast: Podcast): EpisodeWindow = resolve(podcast, clock.instant())

    /**
     * The window [episode] was generated for, or null for an episode generated before windows were
     * recorded or with an unparseable one. A run that reproduces an episode must use this rather
     * than recompute, or it selects from a different window than the episode was built on.
     */
    fun windowOf(episode: Episode): EpisodeWindow? {
        val start = episode.windowStart ?: return null
        val end = episode.windowEnd ?: return null
        return try {
            EpisodeWindow(start = Instant.parse(start), end = Instant.parse(end))
        } catch (e: Exception) {
            log.warn("[Window] Unparseable window [{}, {}) on episode {}: {}", start, end, episode.id, e.message)
            null
        }
    }

    /** The window for the scheduled slot at [windowEnd]. */
    fun resolve(podcast: Podcast, windowEnd: Instant): EpisodeWindow {
        val floor = windowEnd.minus(maxWindowDays(podcast), ChronoUnit.DAYS)
        val previousSlot = previousSlot(podcast, windowEnd, floor)
        val coveredUntil = lastCoveredUntil(podcast, windowEnd)

        val start = when {
            coveredUntil == null || !coveredUntil.isBefore(previousSlot) -> previousSlot
            coveredUntil.isBefore(floor) -> {
                log.warn(
                    "[Window] Last covered window of podcast '{}' ({}) ended at {}, beyond the {}-day cap; " +
                        "starting at {} instead",
                    podcast.name, podcast.id, coveredUntil, maxWindowDays(podcast), floor
                )
                floor
            }
            else -> {
                log.info(
                    "[Window] Extending the window of podcast '{}' ({}) back from {} to {} to cover the gap " +
                        "left by an earlier episode",
                    podcast.name, podcast.id, previousSlot, coveredUntil
                )
                coveredUntil
            }
        }

        return EpisodeWindow(start = start, end = windowEnd)
    }

    /**
     * The podcast's timezone, falling back to UTC when it is unparseable. Shared so the scheduler
     * and the window agree on which zone the cron is read in.
     */
    fun zoneOf(podcast: Podcast): ZoneId {
        return try {
            ZoneId.of(podcast.timezone)
        } catch (_: Exception) {
            log.warn(
                "[Window] Invalid timezone '{}' for podcast '{}' ({}), falling back to UTC",
                podcast.timezone, podcast.name, podcast.id
            )
            ZoneOffset.UTC
        }
    }

    private fun maxWindowDays(podcast: Podcast): Long = appProperties.episode.maxWindowDays.toLong()

    /**
     * The latest cron slot strictly before [windowEnd], or [floor] when the cron has none within
     * the cap. [CronExpression] only walks forward, so the slots from [floor] are enumerated and the
     * last one before [windowEnd] is kept.
     */
    private fun previousSlot(podcast: Podcast, windowEnd: Instant, floor: Instant): Instant {
        val zone = zoneOf(podcast)
        val cron = try {
            CronExpression.parse(podcast.cron)
        } catch (e: Exception) {
            log.warn(
                "[Window] Invalid cron '{}' for podcast '{}' ({}): {}. Using the {}-day cap as the window start",
                podcast.cron, podcast.name, podcast.id, e.message, maxWindowDays(podcast)
            )
            return floor
        }

        val endLocal = LocalDateTime.ofInstant(windowEnd, zone)
        var cursor = LocalDateTime.ofInstant(floor, zone)
        var latest: LocalDateTime? = null
        var steps = 0
        while (steps++ < MAX_SLOT_STEPS) {
            val next = cron.next(cursor) ?: break
            if (!next.isBefore(endLocal)) break
            latest = next
            cursor = next
        }
        if (steps > MAX_SLOT_STEPS) {
            log.warn(
                "[Window] Cron '{}' of podcast '{}' ({}) fires more than {} times within the {}-day cap; " +
                    "using the latest slot found at {}",
                podcast.cron, podcast.name, podcast.id, MAX_SLOT_STEPS, maxWindowDays(podcast), latest
            )
        }

        return latest?.atZone(zone)?.toInstant() ?: floor
    }

    private companion object {
        // The slots inside the cap are enumerated forward, so a cron firing every minute over a
        // multi-day cap would otherwise walk thousands of steps on every scheduler tick.
        const val MAX_SLOT_STEPS = 1_000
    }

    /**
     * Where the podcast's coverage currently ends: the `windowEnd` of the most recent episode that
     * carries a window and was not failed or discarded. Null when no episode has covered a window
     * yet, which is also the case for every episode generated before windows were recorded.
     */
    private fun lastCoveredUntil(podcast: Podcast, windowEnd: Instant): Instant? {
        val covered = episodeRepository.findLatestCoveredWindowEnd(podcast.id, windowEnd.toString())
            ?: return null
        return try {
            Instant.parse(covered)
        } catch (e: Exception) {
            log.warn("[Window] Unparseable window_end '{}' for podcast '{}' ({}): {}", covered, podcast.name, podcast.id, e.message)
            null
        }
    }
}
