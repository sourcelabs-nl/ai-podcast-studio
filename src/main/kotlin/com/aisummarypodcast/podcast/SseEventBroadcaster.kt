package com.aisummarypodcast.podcast

import com.aisummarypodcast.store.PodcastRepository
import tools.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Component
class SseEventBroadcaster(
    private val podcastRepository: PodcastRepository,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val emitters = ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>()

    fun register(userId: String, emitter: SseEmitter) {
        emitters.computeIfAbsent(userId) { CopyOnWriteArrayList() }.add(emitter)
        log.info("SSE emitter registered for user {} (total: {})", userId, emitters[userId]?.size)
    }

    fun remove(userId: String, emitter: SseEmitter) {
        emitters[userId]?.remove(emitter)
        if (emitters[userId]?.isEmpty() == true) {
            emitters.remove(userId)
        }
        log.debug("SSE emitter removed for user {} (remaining: {})", userId, emitters[userId]?.size ?: 0)
    }

    @EventListener
    fun onPodcastEvent(event: PodcastEvent) {
        val userId = resolveUserId(event.podcastId)
        if (userId == null) {
            log.warn("SSE event '{}' for podcast {} — could not resolve userId", event.event, event.podcastId)
            return
        }
        val userEmitters = emitters[userId]
        if (userEmitters == null || userEmitters.isEmpty()) {
            log.info("SSE event '{}' for user {} — no connected emitters", event.event, userId)
            return
        }
        log.info("SSE broadcasting '{}' to {} emitter(s) for user {}", event.event, userEmitters.size, userId)

        val payload = objectMapper.writeValueAsString(
            mapOf(
                "event" to event.event,
                "podcastId" to event.podcastId,
                "entityType" to event.entityType,
                "entityId" to event.entityId,
                "data" to event.data
            )
        )

        val dead = mutableListOf<SseEmitter>()
        for (emitter in userEmitters) {
            try {
                emitter.send(
                    SseEmitter.event()
                        .data(payload)
                )
            } catch (e: Exception) {
                dead.add(emitter)
            }
        }
        for (emitter in dead) {
            remove(userId, emitter)
        }
    }

    fun sendHeartbeat() {
        for ((userId, userEmitters) in emitters) {
            val dead = mutableListOf<SseEmitter>()
            for (emitter in userEmitters) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"))
                } catch (e: Exception) {
                    dead.add(emitter)
                }
            }
            for (emitter in dead) {
                remove(userId, emitter)
            }
        }
    }

    private fun resolveUserId(podcastId: String): String? {
        return podcastRepository.findByIdOrNull(podcastId)?.userId
    }
}
