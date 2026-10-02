package com.aisummarypodcast.tts

import com.aisummarypodcast.user.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/users/{userId}/voices")
class VoiceController(
    private val userService: UserService,
    private val voiceDiscoveryService: VoiceDiscoveryService
) {

    @GetMapping
    fun listVoices(
        @PathVariable userId: String,
        @RequestParam provider: String
    ): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()

        val voices = voiceDiscoveryService.listVoices(userId, provider)
        return ResponseEntity.ok(voices.map { VoiceResponse(it.voiceId, it.name, it.category, it.previewUrl) })
    }
}
