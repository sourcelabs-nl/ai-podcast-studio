package com.aisummarypodcast.podcast

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.net.URLConnection
import java.nio.file.Files

@RestController
@RequestMapping("/users/{userId}/podcasts/{podcastId}/image")
class PodcastImageController(
    private val podcastAccess: PodcastAccess,
    private val podcastImageService: PodcastImageService
) {

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @RequestPart("file") file: MultipartFile
    ): ResponseEntity<Any> {
        podcastAccess.podcast(userId, podcastId)

        val path = podcastImageService.upload(podcastId, file)
        return ResponseEntity.ok(mapOf("path" to path))
    }

    @GetMapping
    fun get(
        @PathVariable userId: String,
        @PathVariable podcastId: String
    ): ResponseEntity<Any> {
        podcastAccess.podcast(userId, podcastId)

        val imagePath = podcastImageService.get(podcastId) ?: return ResponseEntity.notFound().build()
        val contentType = URLConnection.guessContentTypeFromName(imagePath.fileName.toString())
            ?: "application/octet-stream"
        val bytes = Files.readAllBytes(imagePath)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .body(bytes)
    }

    @DeleteMapping
    fun delete(
        @PathVariable userId: String,
        @PathVariable podcastId: String
    ): ResponseEntity<Any> {
        podcastAccess.podcast(userId, podcastId)

        return if (podcastImageService.delete(podcastId)) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }
}
