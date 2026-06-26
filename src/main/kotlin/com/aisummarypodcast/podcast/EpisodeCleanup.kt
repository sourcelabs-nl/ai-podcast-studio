package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class EpisodeCleanup(
    private val episodeService: EpisodeService,
    private val podcastService: PodcastService,
    private val appProperties: AppProperties,
    private val staticFeedExporter: StaticFeedExporter
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 3 * * *")
    fun cleanup() {
        val cutoff = Instant.now().minus(appProperties.episodes.retentionDays.toLong(), ChronoUnit.DAYS).toString()

        for (podcast in podcastService.findAll()) {
            val removed = episodeService.cleanupOldEpisodes(podcast, cutoff)
            if (removed > 0) {
                staticFeedExporter.export(podcast)
            }
        }
    }
}
