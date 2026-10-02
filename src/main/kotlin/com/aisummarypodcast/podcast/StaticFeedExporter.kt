package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.publishing.PodcastPublicationTargetService
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path

@Component
class StaticFeedExporter(
    private val feedGenerator: FeedGenerator,
    private val userRepository: UserRepository,
    private val appProperties: AppProperties,
    private val targetService: PodcastPublicationTargetService,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun export(podcast: Podcast) {
        try {
            val user = userRepository.findByIdOrNull(podcast.userId)
            if (user == null) {
                log.warn("Cannot export static feed for podcast {}: user {} not found", podcast.id, podcast.userId)
                return
            }

            val publicUrl = resolvePublicUrl(podcast.id)
            val publishedTarget = if (publicUrl != null) "ftp" else null
            val baseUrl = publicUrl ?: appProperties.feed.staticBaseUrl ?: appProperties.feed.baseUrl
            val xml = feedGenerator.generate(podcast, user, baseUrl, publicUrl, publishedTarget)

            val podcastDir = Path.of(appProperties.episodes.directory, podcast.id)

            Files.createDirectories(podcastDir)
            Files.writeString(podcastDir.resolve("feed.xml"), xml)

            log.info("Exported static feed.xml for podcast {}", podcast.id)
        } catch (e: Exception) {
            log.warn("Failed to export static feed.xml for podcast {}: {}", podcast.id, e.message)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolvePublicUrl(podcastId: String): String? {
        val ftpTarget = targetService.get(podcastId, "ftp") ?: return null
        if (!ftpTarget.enabled) return null
        return try {
            val config = objectMapper.readValue(ftpTarget.config, Map::class.java) as Map<String, Any>
            val baseUrl = (config["publicUrl"] as? String)?.takeIf { it.isNotBlank() }
                ?.let { if (it.endsWith("/")) it else "$it/" }
                ?: return null
            val remotePath = (config["remotePath"] as? String)?.takeIf { it.isNotBlank() }
                ?.let { if (it.endsWith("/")) it else "$it/" }
            val podcastPath = remotePath ?: "/$podcastId/"
            "$baseUrl${podcastPath.trimStart('/')}"
        } catch (_: Exception) {
            null
        }
    }
}
