package com.aisummarypodcast.publishing

import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.annotation.JsonNaming
import java.nio.file.Path

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SoundCloudTokenResponse(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresIn: Long? = null,
    val tokenType: String? = null,
    val scope: String? = null
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SoundCloudTrackResponse(
    val id: Long,
    val permalinkUrl: String,
    val title: String? = null
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SoundCloudTrackListResponse(
    val collection: List<SoundCloudTrackSummary> = emptyList(),
    val nextHref: String? = null
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SoundCloudTrackSummary(
    val id: Long,
    val title: String? = null,
    val createdAt: String? = null,
    val duration: Long? = null,
    val permalinkUrl: String? = null
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SoundCloudPlaylistResponse(
    val id: Long,
    val permalinkUrl: String? = null
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SoundCloudPlaylistDetailResponse(
    val id: Long,
    val tracks: List<SoundCloudPlaylistTrack> = emptyList()
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SoundCloudPlaylistTrack(
    val id: Long
)

data class TrackUploadRequest(
    val title: String,
    val description: String,
    val tagList: String,
    val permalink: String,
    val audioFilePath: Path
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SoundCloudQuota(
    val unlimitedUploadQuota: Boolean = false,
    val uploadSecondsUsed: Long = 0,
    val uploadSecondsLeft: Long = 0
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SoundCloudMeResponse(
    val id: Long,
    val username: String? = null,
    val plan: String? = null,
    val quota: SoundCloudQuota? = null
)

/**
 * SoundCloud refused the upload because the account's plan does not permit uploads through the API.
 *
 * Distinct from running out of upload quota, and it must stay distinct: no amount of deleting old
 * tracks makes room for it, so the publisher must not trade away published episodes trying. It first
 * appeared on 11 September 2026, on an account holding six tracks with 1610 seconds of quota free,
 * hours after the same call had succeeded.
 */
class SoundCloudUploadNotPermittedException(message: String) : RuntimeException(message)

@Service
class SoundCloudClient(restTemplateBuilder: RestTemplateBuilder, private val jsonMapper: JsonMapper) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val restTemplate: RestTemplate = restTemplateBuilder.build()

    companion object {
        /** Marks the 403 body SoundCloud returns when the account's plan bars API uploads. */
        private const val SUBSCRIPTION_REQUIRED = "subscription is required"
    }

    fun getMe(accessToken: String): SoundCloudMeResponse {
        val headers = HttpHeaders().apply {
            setBearerAuth(accessToken)
        }
        val response = restTemplate.exchange(
            "https://api.soundcloud.com/me",
            HttpMethod.GET,
            HttpEntity<Any>(headers),
            SoundCloudMeResponse::class.java
        )
        return response.body ?: throw RuntimeException("Empty response from SoundCloud /me")
    }

    fun getMyTracks(accessToken: String, limit: Int = 200): SoundCloudTrackListResponse {
        val headers = HttpHeaders().apply {
            setBearerAuth(accessToken)
        }
        val response = restTemplate.exchange(
            "https://api.soundcloud.com/me/tracks?linked_partitioning=true&limit=$limit",
            HttpMethod.GET,
            HttpEntity<Any>(headers),
            SoundCloudTrackListResponse::class.java
        )
        return response.body ?: SoundCloudTrackListResponse()
    }

    fun exchangeCodeForTokens(
        code: String,
        clientId: String,
        clientSecret: String,
        redirectUri: String,
        codeVerifier: String
    ): SoundCloudTokenResponse {
        val body = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("client_id", clientId)
            add("client_secret", clientSecret)
            add("redirect_uri", redirectUri)
            add("code", code)
            add("code_verifier", codeVerifier)
        }

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
        }

        log.info("Exchanging authorization code for SoundCloud tokens")
        val response = restTemplate.postForEntity(
            "https://secure.soundcloud.com/oauth/token",
            HttpEntity(body, headers),
            SoundCloudTokenResponse::class.java
        )
        return response.body ?: throw RuntimeException("Empty response from SoundCloud token exchange")
    }

    fun refreshAccessToken(
        refreshToken: String,
        clientId: String,
        clientSecret: String
    ): SoundCloudTokenResponse {
        val body = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "refresh_token")
            add("client_id", clientId)
            add("client_secret", clientSecret)
            add("refresh_token", refreshToken)
        }

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
        }

        log.info("Refreshing SoundCloud access token")
        val response = restTemplate.postForEntity(
            "https://secure.soundcloud.com/oauth/token",
            HttpEntity(body, headers),
            SoundCloudTokenResponse::class.java
        )
        return response.body ?: throw RuntimeException("Empty response from SoundCloud token refresh")
    }

    fun uploadTrack(
        accessToken: String,
        request: TrackUploadRequest
    ): SoundCloudTrackResponse {
        val body = LinkedMultiValueMap<String, Any>().apply {
            add("track[title]", request.title)
            add("track[description]", request.description)
            add("track[tag_list]", request.tagList)
            add("track[permalink]", request.permalink)
            add("track[sharing]", "public")
            add("track[asset_data]", FileSystemResource(request.audioFilePath))
        }

        val headers = HttpHeaders().apply {
            contentType = MediaType.MULTIPART_FORM_DATA
            setBearerAuth(accessToken)
        }

        log.info("Uploading track to SoundCloud: {}", request.title)
        val response = try {
            restTemplate.exchange(
                "https://api.soundcloud.com/tracks",
                HttpMethod.POST,
                HttpEntity(body, headers),
                SoundCloudTrackResponse::class.java
            )
        } catch (e: HttpClientErrorException.Forbidden) {
            val error = e.responseBodyAsString
            if (SUBSCRIPTION_REQUIRED in error) {
                log.error("SoundCloud refused the upload of {}: {}", request.title, error)
                throw SoundCloudUploadNotPermittedException(describeRefusal(error))
            }
            throw e
        }
        return response.body ?: throw RuntimeException("Empty response from SoundCloud track upload")
    }

    /**
     * SoundCloud's own sentence for why it refused, so the reason reaches the dashboard as prose
     * rather than as the raw error envelope. The full body is logged either way.
     */
    private fun describeRefusal(error: String): String {
        val message = try {
            jsonMapper.readTree(error).get("message")?.asString()
        } catch (e: Exception) {
            log.warn("Could not read the SoundCloud error body as JSON: {}", e.message)
            null
        }
        return "SoundCloud refused the upload: ${message?.takeIf { it.isNotBlank() } ?: error}"
    }

    fun deleteTrack(accessToken: String, trackId: Long) {
        val headers = HttpHeaders().apply {
            setBearerAuth(accessToken)
        }

        log.info("Deleting SoundCloud track {}", trackId)
        try {
            restTemplate.exchange(
                "https://api.soundcloud.com/tracks/$trackId",
                HttpMethod.DELETE,
                HttpEntity<Any>(headers),
                Void::class.java
            )
        } catch (e: HttpClientErrorException.NotFound) {
            log.info("SoundCloud track {} already deleted (404)", trackId)
        }
    }

    fun updateTrack(
        accessToken: String,
        trackId: Long,
        permalink: String? = null,
        description: String? = null
    ): SoundCloudTrackResponse {
        val trackFields = mutableMapOf<String, String>()
        permalink?.let { trackFields["permalink"] = it }
        description?.let { trackFields["description"] = it }
        require(trackFields.isNotEmpty()) { "At least one field must be provided for track update" }
        val body = mapOf("track" to trackFields)

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(accessToken)
        }

        log.info("Updating SoundCloud track {} permalink to {}", trackId, permalink)
        val response = restTemplate.exchange(
            "https://api.soundcloud.com/tracks/$trackId",
            HttpMethod.PUT,
            HttpEntity(body, headers),
            SoundCloudTrackResponse::class.java
        )
        return response.body ?: throw RuntimeException("Empty response from SoundCloud track update")
    }

    fun createPlaylist(
        accessToken: String,
        title: String,
        trackIds: List<Long>
    ): SoundCloudPlaylistResponse {
        val body = mapOf(
            "playlist" to mapOf(
                "title" to title,
                "sharing" to "public",
                "tracks" to trackIds.map { mapOf("id" to it.toString()) }
            )
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(accessToken)
        }

        log.info("Creating SoundCloud playlist: {}", title)
        val response = restTemplate.postForEntity(
            "https://api.soundcloud.com/playlists",
            HttpEntity(body, headers),
            SoundCloudPlaylistResponse::class.java
        )
        return response.body ?: throw RuntimeException("Empty response from SoundCloud playlist creation")
    }

    fun getPlaylist(
        accessToken: String,
        playlistId: Long
    ): SoundCloudPlaylistDetailResponse {
        val headers = HttpHeaders().apply {
            setBearerAuth(accessToken)
        }

        log.info("Fetching SoundCloud playlist {}", playlistId)
        val response = restTemplate.exchange(
            "https://api.soundcloud.com/playlists/$playlistId",
            HttpMethod.GET,
            HttpEntity<Any>(headers),
            SoundCloudPlaylistDetailResponse::class.java
        )
        return response.body ?: throw RuntimeException("Empty response from SoundCloud playlist fetch")
    }

    fun addTrackToPlaylist(
        accessToken: String,
        playlistId: Long,
        trackIds: List<Long>
    ): SoundCloudPlaylistResponse {
        val body = mapOf(
            "playlist" to mapOf(
                "tracks" to trackIds.map { mapOf("id" to it.toString()) }
            )
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(accessToken)
        }

        log.info("Adding tracks to SoundCloud playlist {}", playlistId)
        val response = restTemplate.exchange(
            "https://api.soundcloud.com/playlists/$playlistId",
            HttpMethod.PUT,
            HttpEntity(body, headers),
            SoundCloudPlaylistResponse::class.java
        )
        return response.body ?: throw RuntimeException("Empty response from SoundCloud playlist update")
    }
}
