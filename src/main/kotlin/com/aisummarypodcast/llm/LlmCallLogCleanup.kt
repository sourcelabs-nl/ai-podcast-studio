package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Ages out the per-request call log.
 *
 * Runs at 04:00, after the LLM cache cleanup and outside the generation window, so the delete never
 * competes with a running pipeline for SQLite's single writer.
 */
@Component
class LlmCallLogCleanup(
    private val llmCallLogService: LlmCallLogService,
    private val appProperties: AppProperties
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 4 * * *")
    fun cleanup() {
        val retention = appProperties.llm.callLog.retention
        val cutoff = Instant.now().minus(retention)
        llmCallLogService.deleteOlderThan(cutoff)
        log.info("LLM call log cleanup: deleted records older than {} (cutoff: {})", retention, cutoff)
    }
}
