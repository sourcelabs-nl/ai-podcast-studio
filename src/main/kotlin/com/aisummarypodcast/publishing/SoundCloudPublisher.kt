package com.aisummarypodcast.publishing

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.podcast.EpisodeSourcesGenerator
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.Podcast
import kotlinx.coroutines.delay
import tools.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Component
class SoundCloudPublisher(
    private val soundCloudClient: SoundCloudClient,
    private val tokenManager: SoundCloudTokenManager,
    private val targetService: PodcastPublicationTargetService,
    private val objectMapper: ObjectMapper,
    private val appProperties: AppProperties,
    private val episodeSourcesGenerator: EpisodeSourcesGenerator
) : EpisodePublisher {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val TARGET_NAME = "soundcloud"

        // Extra headroom (seconds) added on top of the episode's own duration when planning how much
        // quota to free, to absorb SoundCloud's quota rounding and any duration estimate error.
        private const val QUOTA_BUFFER_SECONDS = 120L

        // SoundCloud does not reflect freed upload time immediately after a track is deleted, so we
        // pause this long between deleting old tracks and uploading the new one to let the freed
        // quota register on their side. Without this wait the upload is rejected for lack of quota.
        private const val QUOTA_SETTLE_MILLIS = 4000L
    }

    override fun targetName(): String = TARGET_NAME

    override suspend fun publish(episode: Episode, podcast: Podcast, userId: String): PublishResult {
        val accessToken = tokenManager.getValidAccessToken(userId)

        // Make room first when the account's remaining quota is too small for this episode: delete
        // the oldest tracks for this podcast, then wait for SoundCloud to register the freed space.
        freeQuotaIfNeeded(accessToken, podcast, episode)

        val episodeDate = LocalDate.parse(
            episode.generatedAt,
            DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)
        )
        val title = "${podcast.name} - $episodeDate"
        val permalink = buildPermalink(podcast.name, episodeDate)
        val description = buildDescription(episode, podcast)
        val tagList = buildTagList(podcast.topic)

        val response = soundCloudClient.uploadTrack(
            accessToken = accessToken,
            request = TrackUploadRequest(
                title = title,
                description = description,
                tagList = tagList,
                permalink = permalink,
                audioFilePath = Path.of(episode.audioFilePath!!)
            )
        )

        return PublishResult(
            externalId = response.id.toString(),
            externalUrl = response.permalinkUrl
        )
    }

    override fun unpublish(userId: String, externalId: String) {
        val accessToken = tokenManager.getValidAccessToken(userId)
        val trackId = externalId.toLong()
        soundCloudClient.deleteTrack(accessToken, trackId)
        log.info("Unpublished (deleted) SoundCloud track {}", trackId)
    }

    /**
     * Frees enough SoundCloud upload quota for [episode] by deleting the oldest tracks belonging to
     * this podcast, then waits for SoundCloud to register the freed space (see [QUOTA_SETTLE_MILLIS]).
     * No-op for unlimited accounts or when there is already enough room. The episode's
     * `durationSeconds` defines how much headroom is needed; when it is unknown the legacy rule (any
     * remaining quota is enough) applies. Deletes oldest-first, just enough (plus a safety buffer)
     * to fit, so we keep as much recent history as possible.
     */
    private suspend fun freeQuotaIfNeeded(accessToken: String, podcast: Podcast, episode: Episode) {
        val quota = soundCloudClient.getMe(accessToken).quota
        if (quota == null || quota.unlimitedUploadQuota) return

        val requiredSeconds = episode.durationSeconds?.toLong()
        val exceeded = if (requiredSeconds != null) {
            quota.uploadSecondsLeft < requiredSeconds
        } else {
            quota.uploadSecondsLeft <= 0
        }
        if (!exceeded) return

        val secondsToFree = (requiredSeconds ?: 1L) - quota.uploadSecondsLeft + QUOTA_BUFFER_SECONDS

        val oldestFirst = soundCloudClient.getMyTracks(accessToken).collection
            .filter { it.title?.startsWith(podcast.name) == true }
            .sortedBy { it.createdAt ?: "" }

        var freed = 0L
        var deleted = 0
        for (track in oldestFirst) {
            if (freed >= secondsToFree) break
            soundCloudClient.deleteTrack(accessToken, track.id)
            freed += (track.duration ?: 0L) / 1000
            deleted++
            log.info("Deleted oldest SoundCloud track {} ({}) to free upload quota", track.id, track.title)
        }

        if (deleted == 0) {
            log.warn(
                "SoundCloud upload quota exceeded (need ~{}s) but no deletable tracks found for podcast {}",
                secondsToFree, podcast.id
            )
            return
        }

        log.info(
            "Freed ~{}s by deleting {} track(s); waiting {}ms for SoundCloud to register the space before upload",
            freed, deleted, QUOTA_SETTLE_MILLIS
        )
        delay(QUOTA_SETTLE_MILLIS)
    }

    override fun update(episode: Episode, podcast: Podcast, userId: String, externalId: String): PublishResult {
        val accessToken = tokenManager.getValidAccessToken(userId)
        val trackId = externalId.toLong()
        val description = buildDescription(episode, podcast)
        val response = soundCloudClient.updateTrack(accessToken, trackId, description = description)
        log.info("Updated SoundCloud track {} description for episode {}", trackId, episode.id)
        return PublishResult(
            externalId = externalId,
            externalUrl = response.permalinkUrl
        )
    }

    private fun getPlaylistId(podcastId: String): Long? {
        val target = targetService.get(podcastId, TARGET_NAME) ?: return null
        val config = objectMapper.readTree(target.config)
        return config.get("playlistId")?.asText()?.toLongOrNull()
    }

    private fun savePlaylistId(podcastId: String, playlistId: Long) {
        val target = targetService.get(podcastId, TARGET_NAME)
        val config = target?.config?.let { objectMapper.readTree(it) }
            ?.let { (it as tools.jackson.databind.node.ObjectNode).put("playlistId", playlistId.toString()) }
            ?: objectMapper.createObjectNode().put("playlistId", playlistId.toString())
        targetService.upsert(
            podcastId,
            TARGET_NAME,
            objectMapper.writeValueAsString(config),
            target?.enabled ?: true,
            target?.autoPublish ?: false
        )
    }

    fun updateTrackPermalinks(podcast: Podcast, userId: String, episodes: List<Episode>, publications: List<com.aisummarypodcast.store.EpisodePublication>): Set<Long> {
        val accessToken = tokenManager.getValidAccessToken(userId)
        val episodeById = episodes.associateBy { it.id }
        val staleTrackIds = mutableSetOf<Long>()

        for (publication in publications) {
            val episode = episodeById[publication.episodeId] ?: continue
            val trackId = publication.externalId?.toLongOrNull() ?: continue
            val episodeDate = LocalDate.parse(
                episode.generatedAt,
                DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)
            )
            val permalink = buildPermalink(podcast.name, episodeDate)
            val description = buildDescription(episode, podcast)
            try {
                soundCloudClient.updateTrack(accessToken, trackId, permalink = permalink, description = description)
            } catch (e: HttpClientErrorException.NotFound) {
                log.warn("SoundCloud track {} not found (404), skipping permalink update", trackId)
                staleTrackIds.add(trackId)
            }
        }
        return staleTrackIds
    }

    fun rebuildPlaylist(podcast: Podcast, userId: String, trackIds: List<Long>) {
        val accessToken = tokenManager.getValidAccessToken(userId)
        val playlistId = getPlaylistId(podcast.id)

        if (playlistId == null) {
            val playlist = soundCloudClient.createPlaylist(accessToken, podcast.name, trackIds)
            savePlaylistId(podcast.id, playlist.id)
            log.info("Created SoundCloud playlist {} with {} tracks for podcast {}", playlist.id, trackIds.size, podcast.id)
            return
        }

        try {
            soundCloudClient.addTrackToPlaylist(accessToken, playlistId, trackIds)
            log.info("Rebuilt SoundCloud playlist {} with {} tracks", playlistId, trackIds.size)
        } catch (e: HttpClientErrorException.NotFound) {
            log.warn("SoundCloud playlist {} not found, creating new playlist", playlistId)
            val playlist = soundCloudClient.createPlaylist(accessToken, podcast.name, trackIds)
            savePlaylistId(podcast.id, playlist.id)
            log.info("Created SoundCloud playlist {} with {} tracks for podcast {}", playlist.id, trackIds.size, podcast.id)
        }
    }

    private fun buildDescription(episode: Episode, podcast: Podcast): String {
        val summary = episode.showNotes ?: episode.recap ?: episode.scriptText.take(500)
        val baseUrl = appProperties.feed.staticBaseUrl ?: appProperties.feed.baseUrl
        val slug = episodeSourcesGenerator.deriveSlug(episode)
        val sourcesUrl = "$baseUrl/data/${podcast.id}/episodes/$slug-sources.html"
        return buildString {
            append(summary)
            append("\n\nFor the full list of sources and show notes: $sourcesUrl")
            appProperties.feed.ownerEmail?.let { append("\n\nTips, comments, or feedback? Mail us at $it") }
        }
    }

    private fun buildPermalink(podcastName: String, episodeDate: LocalDate): String {
        return "${podcastName}-${episodeDate}"
            .lowercase()
            .replace(Regex("[^a-z0-9-]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
    }

    private fun buildTagList(topic: String): String {
        return topic.split(",", ";")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ") { tag ->
                if (tag.contains(" ")) "\"$tag\"" else tag
            }
    }
}
