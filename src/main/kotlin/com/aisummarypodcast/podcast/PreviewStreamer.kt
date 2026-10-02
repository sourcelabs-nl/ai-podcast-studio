package com.aisummarypodcast.podcast

import com.aisummarypodcast.store.Podcast
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import tools.jackson.databind.ObjectMapper

/**
 * Runs the pipeline preview for a podcast and streams it over SSE: `progress` events while it
 * runs, then one `result` (or `error`) event and `complete`. A `heartbeat` every 15 seconds keeps
 * the connection open through the minutes the pipeline takes.
 */
@Component
class PreviewStreamer(
    private val podcastService: PodcastService,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val previewScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private companion object {
        const val SSE_TIMEOUT_MS = 600_000L
        const val HEARTBEAT_INTERVAL_MS = 15_000L
    }

    @PreDestroy
    fun onDestroy() {
        previewScope.cancel()
    }

    /** Starts the preview in the background and returns the emitter it streams into. */
    fun stream(podcast: Podcast): SseEmitter {
        val podcastId = podcast.id
        val sseEmitter = SseEmitter(SSE_TIMEOUT_MS)
        sseEmitter.onCompletion { log.debug("Preview SSE completed for podcast {}", podcastId) }
        sseEmitter.onTimeout { log.warn("Preview SSE timed out for podcast {}", podcastId) }
        sseEmitter.onError { e -> log.error("Preview SSE error for podcast {}: {}", podcastId, e.message) }

        log.info("Preview SSE requested for podcast {}", podcastId)

        val emitter = SafeSseEmitter(sseEmitter)
        previewScope.launch { runPreview(emitter, podcast) }
        return sseEmitter
    }

    private suspend fun runPreview(emitter: SafeSseEmitter, podcast: Podcast) {
        val heartbeat = previewScope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                try {
                    emitter.send("heartbeat", "{}")
                } catch (e: Exception) {
                    log.debug("Heartbeat send failed for podcast {}: {}", podcast.id, e.message)
                    break
                }
            }
        }
        try {
            val result = podcastService.previewBriefing(podcast) { stage, detail ->
                try {
                    emitter.send("progress", objectMapper.writeValueAsString(mapOf("stage" to stage) + detail))
                } catch (e: Exception) {
                    log.debug("Failed to send progress event: {}", e.message)
                }
            }

            val payload = if (result != null) {
                mapOf("scriptText" to result.script, "style" to podcast.style.value, "articleIds" to result.articleIds)
            } else {
                mapOf("message" to "No relevant articles available for preview")
            }
            emitter.send("result", objectMapper.writeValueAsString(payload))
            emitter.send("complete", "")
            emitter.complete()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Preview pipeline failed for podcast {}: {}", podcast.id, e.message, e)
            try {
                emitter.send("error", objectMapper.writeValueAsString(mapOf("message" to (e.message ?: "Preview generation failed"))))
                emitter.complete()
            } catch (sendError: Exception) {
                emitter.completeWithError(e)
            }
        } finally {
            heartbeat.cancelAndJoin()
        }
    }
}
