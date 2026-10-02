package com.aisummarypodcast.tts

import com.aisummarypodcast.podcast.PodcastAccess
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
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import tools.jackson.databind.ObjectMapper

/**
 * Audio for a script that belongs to no episode. The sample is a plain synchronous request because
 * it is one chunk of audio; the full script is dozens of chunks and runs for minutes, which would
 * exceed the Spring MVC async-request timeout, so it streams progress over SSE and hands back an id
 * the finished file is fetched with.
 */
@RestController
@RequestMapping("/users/{userId}/podcasts/{podcastId}/preview/audio")
class PreviewAudioController(
    private val podcastAccess: PodcastAccess,
    private val previewAudioService: PreviewAudioService,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val previewAudioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private companion object {
        const val SSE_TIMEOUT_MS = 1_800_000L
        const val HEARTBEAT_INTERVAL_MS = 15_000L
    }

    @PreDestroy
    fun onDestroy() {
        previewAudioScope.cancel()
    }

    @PostMapping("/sample")
    suspend fun sample(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @RequestBody request: PreviewAudioRequest
    ): ResponseEntity<Any> {
        val podcast = podcastAccess.podcast(userId, podcastId)
        if (request.scriptText.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "scriptText must not be blank"))
        }

        val audio = previewAudioService.synthesizeSample(podcast, request.scriptText)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("audio/mpeg"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"preview-sample.mp3\"")
            .body(audio)
    }

    @PostMapping("/estimate")
    fun estimate(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @RequestBody request: PreviewAudioRequest
    ): ResponseEntity<Any> {
        val podcast = podcastAccess.podcast(userId, podcastId)
        if (request.scriptText.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "scriptText must not be blank"))
        }
        return ResponseEntity.ok(previewAudioService.estimate(podcast, request.scriptText).toResponse())
    }

    @PostMapping(produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun generateFullAudio(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @RequestBody request: PreviewAudioRequest
    ): Any {
        val podcast = podcastAccess.podcast(userId, podcastId)
        // The handler produces text/event-stream, which no converter can render a JSON body into,
        // so a rejected request answers with a bare status.
        if (request.scriptText.isBlank()) return ResponseEntity.badRequest().build<Any>()

        val emitter = SseEmitter(SSE_TIMEOUT_MS)
        emitter.onCompletion { log.debug("Preview audio SSE completed for podcast {}", podcastId) }
        emitter.onTimeout { log.warn("Preview audio SSE timed out for podcast {}", podcastId) }
        emitter.onError { e -> log.error("Preview audio SSE error for podcast {}: {}", podcastId, e.message) }

        previewAudioScope.launch { streamFullAudio(emitter, podcast, request.scriptText) }
        return emitter
    }

    @GetMapping("/{audioId}")
    fun streamStoredAudio(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable audioId: String
    ): ResponseEntity<Resource> {
        podcastAccess.podcast(userId, podcastId)
        val file = previewAudioService.findStoredAudio(podcastId, audioId) ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("audio/mpeg"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"preview-$audioId.mp3\"")
            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
            .body(FileSystemResource(file))
    }

    private suspend fun streamFullAudio(emitter: SseEmitter, podcast: Podcast, script: String) {
        // Inworld synthesizes chunks concurrently, so completions arrive from several coroutines at
        // once. SseEmitter is not safe for concurrent send(), so every write to this stream —
        // progress, result, and heartbeat alike — goes through one lock.
        val sendLock = Any()
        val heartbeat = previewAudioScope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                try {
                    synchronized(sendLock) { emitter.send(SseEmitter.event().name("heartbeat").data("{}")) }
                } catch (e: Exception) {
                    log.debug("Heartbeat send failed for podcast {}: {}", podcast.id, e.message)
                    break
                }
            }
        }
        try {
            val audioId = previewAudioService.generateFullAudio(podcast, script) { completed, total ->
                // A dropped progress event must not abort a synthesis run that is minutes in.
                try {
                    synchronized(sendLock) {
                        send(emitter, "progress", mapOf("stage" to "synthesizing", "chunk" to completed, "total" to total))
                    }
                } catch (e: Exception) {
                    log.debug("Failed to send preview audio progress event: {}", e.message)
                }
            }
            synchronized(sendLock) {
                send(emitter, "result", mapOf("audioId" to audioId))
                emitter.send(SseEmitter.event().name("complete").data(""))
            }
            emitter.complete()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Preview audio generation failed for podcast {}: {}", podcast.id, e.message, e)
            try {
                synchronized(sendLock) {
                    send(emitter, "error", mapOf("message" to (e.message ?: "Preview audio generation failed")))
                }
                emitter.complete()
            } catch (_: Exception) {
                emitter.completeWithError(e)
            }
        } finally {
            heartbeat.cancelAndJoin()
        }
    }

    private fun send(emitter: SseEmitter, name: String, payload: Map<String, Any>) {
        emitter.send(SseEmitter.event().name(name).data(objectMapper.writeValueAsString(payload)))
    }
}
