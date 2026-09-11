package com.aisummarypodcast.publishing

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.podcast.EpisodeSourcesGenerator
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.Podcast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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

    /**
     * Uploads the episode, freeing upload quota only if a first attempt has actually been refused.
     *
     * The order matters. Freeing quota deletes published episodes for good, so it must never run on
     * a prediction: an upload the account was going to refuse for a reason no deletion can fix would
     * cost the back catalogue and publish nothing. A [SoundCloudUploadNotPermittedException] is
     * exactly that reason and is not an [HttpClientErrorException], so it leaves every track intact.
     *
     * The cost of attempting first is one wasted upload on a genuinely full account, once per run.
     */
    override suspend fun publish(episode: Episode, podcast: Podcast, userId: String): PublishResult = withContext(Dispatchers.IO) {
        val accessToken = tokenManager.getValidAccessToken(userId)
        val upload = buildUploadRequest(episode, podcast)

        val response = try {
            soundCloudClient.uploadTrack(accessToken, upload)
        } catch (e: HttpClientErrorException) {
            if (!freeQuotaIfNeeded(accessToken, podcast, episode)) throw e
            soundCloudClient.uploadTrack(accessToken, upload)
        }

        PublishResult(
            externalId = response.id.toString(),
            externalUrl = response.permalinkUrl
        )
    }

    private fun buildUploadRequest(episode: Episode, podcast: Podcast): TrackUploadRequest {
        val episodeDate = episodeDate(episode)
        return TrackUploadRequest(
            title = "${podcast.name} - $episodeDate",
            description = buildDescription(episode, podcast),
            tagList = buildTagList(podcast.topic),
            permalink = buildPermalink(podcast.name, episodeDate),
            audioFilePath = Path.of(episode.audioFilePath!!)
        )
    }

    private fun episodeDate(episode: Episode): LocalDate = LocalDate.parse(
        episode.generatedAt,
        DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)
    )

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
     *
     * Returns true when at least one track was deleted, meaning a retry now has room it did not have
     * before. Returns false when nothing was deleted, so the caller knows a retry would be pointless.
     */
    private suspend fun freeQuotaIfNeeded(accessToken: String, podcast: Podcast, episode: Episode): Boolean {
        val quota = soundCloudClient.getMe(accessToken).quota
        if (quota == null || quota.unlimitedUploadQuota) return false

        val requiredSeconds = episode.durationSeconds?.toLong()
        val exceeded = if (requiredSeconds != null) {
            quota.uploadSecondsLeft < requiredSeconds
        } else {
            quota.uploadSecondsLeft <= 0
        }
        if (!exceeded) return false

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
            return false
        }

        log.info(
            "Freed ~{}s by deleting {} track(s); waiting {}ms for SoundCloud to register the space before upload",
            freed, deleted, QUOTA_SETTLE_MILLIS
        )
        delay(QUOTA_SETTLE_MILLIS)
        return true
    }

    /**
     * Replaces the published track by deleting it and uploading the episode's current audio.
     *
     * SoundCloud offers no way to swap a track's audio in place: [SoundCloudClient.updateTrack]
     * carries metadata only. Updating just the description, which is what this used to do, reported
     * success while leaving the previous audio live, so a regenerated episode never reached
     * listeners. Episode 184 kept its truncated opening on SoundCloud after both its script and its
     * audio had been repaired, and the log line claimed the update had worked.
     *
     * The replacement is uploaded before the old track is deleted, so a refused upload leaves the
     * episode published rather than removing it and putting nothing back. That ordering costs one
     * step: SoundCloud holds the canonical permalink for as long as the old track exists, so the
     * replacement is created under a suffixed variant and claims the canonical slug once the old
     * track is gone. Deleting also returns the old track's seconds to the upload quota, which is why
     * the [QUOTA_SETTLE_MILLIS] pause belongs here: without it a following [publish] reads a quota
     * that has not yet registered this deletion and deletes further, older episodes to make room
     * that already exists.
     *
     * A republish is idempotent: [SoundCloudClient.deleteTrack] treats an already-deleted track as
     * done, so an attempt that uploaded the replacement and then failed can simply be retried.
     *
     * Returns the NEW track id; the one passed in is dead once this returns.
     */
    override suspend fun update(episode: Episode, podcast: Podcast, userId: String, externalId: String): PublishResult {
        val accessToken = tokenManager.getValidAccessToken(userId)
        val trackId = externalId.toLong()

        val uploaded = publish(episode, podcast, userId)

        withContext(Dispatchers.IO) { soundCloudClient.deleteTrack(accessToken, trackId) }
        log.info("Deleted SoundCloud track {} after replacing episode {}'s audio", trackId, episode.id)
        delay(QUOTA_SETTLE_MILLIS)

        val claimed = withContext(Dispatchers.IO) {
            soundCloudClient.updateTrack(
                accessToken = accessToken,
                trackId = uploaded.externalId.toLong(),
                permalink = buildPermalink(podcast.name, episodeDate(episode))
            )
        }

        log.info("Replaced SoundCloud track {} with {} for episode {}", trackId, uploaded.externalId, episode.id)
        return uploaded.copy(externalUrl = claimed.permalinkUrl)
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
