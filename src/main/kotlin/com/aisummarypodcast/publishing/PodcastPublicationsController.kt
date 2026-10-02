package com.aisummarypodcast.publishing

import com.aisummarypodcast.api.PageParams
import com.aisummarypodcast.podcast.PodcastAccess
import com.aisummarypodcast.podcast.toResponse
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
    private val podcastAccess: PodcastAccess,
    private val publishingService: PublishingService
) {

    @GetMapping
    fun list(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") pageSize: Int
    ): ResponseEntity<Any> {
        podcastAccess.podcast(userId, podcastId)

        val pageable = PageParams.of(page, pageSize).toPageable(Sort.by(Sort.Direction.DESC, "createdAt", "id"))
        val result = publishingService.listByPodcast(podcastId, pageable)
        return ResponseEntity.ok(result.toResponse())
    }
}
