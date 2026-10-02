package com.aisummarypodcast.podcast

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * An [SseEmitter] that several coroutines can write to. The emitter itself is not safe for
 * concurrent sends, and a stream is written by its heartbeat, its progress callbacks and its
 * finishing events at once, so every send and every completion takes the same lock.
 * Hand [emitter] to Spring MVC as the response.
 */
class SafeSseEmitter(val emitter: SseEmitter) {

    private val lock = Any()

    fun send(eventName: String, data: String) {
        synchronized(lock) { emitter.send(SseEmitter.event().name(eventName).data(data)) }
    }

    fun complete() {
        synchronized(lock) { emitter.complete() }
    }

    fun completeWithError(error: Throwable) {
        synchronized(lock) { emitter.completeWithError(error) }
    }
}
