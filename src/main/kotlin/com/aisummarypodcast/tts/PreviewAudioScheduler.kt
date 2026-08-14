package com.aisummarypodcast.tts

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Sweeps expired preview audio. Preview audio has no episode row to hang a lifetime on, so a
 * time-based sweep is the only thing that keeps the directory from growing without bound.
 */
@Component
class PreviewAudioScheduler(
    private val previewAudioService: PreviewAudioService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = $$"${app.preview-audio.sweep-cron}")
    fun sweep() {
        try {
            previewAudioService.sweepExpiredAudio()
        } catch (e: Exception) {
            log.error("Preview audio sweep failed: {}", e.message, e)
        }
    }
}
