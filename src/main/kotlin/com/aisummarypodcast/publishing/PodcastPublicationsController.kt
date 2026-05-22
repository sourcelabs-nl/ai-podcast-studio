package com.aisummarypodcast.publishing

import com.aisummarypodcast.podcast.PodcastService
import com.aisummarypodcast.podcast.toResponse
import com.aisummarypodcast.user.UserService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users/{userId}/podcasts/{podcastId}/publications")
class PodcastPublicationsController(
    private val userService: UserService,
    private val podcastService: PodcastService,
    private val publishingService: PublishingService
) {

    @GetMapping
    fun list(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") pageSize: Int
    ): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val podcast = podcastService.findById(podcastId) ?: return ResponseEntity.notFound().build()
        if (podcast.userId != userId) return ResponseEntity.notFound().build()

        if (page < 0) return ResponseEntity.badRequest().body(mapOf("error" to "page must be >= 0"))
        if (pageSize < 1 || pageSize > 200) return ResponseEntity.badRequest().body(mapOf("error" to "pageSize must be in [1, 200]"))

        val pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "createdAt", "id"))
        val result = publishingService.listByPodcast(podcastId, pageable)
        return ResponseEntity.ok(result.toResponse())
    }
}
