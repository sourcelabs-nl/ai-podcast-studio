package com.aisummarypodcast.publishing

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.podcast.EpisodeService
import com.aisummarypodcast.podcast.EpisodeSourcesGenerator
import com.aisummarypodcast.podcast.FeedGenerator
import com.aisummarypodcast.podcast.PodcastImageService
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastRepository
import com.aisummarypodcast.store.UserRepository
import com.aisummarypodcast.user.UserProviderConfigService
import com.aisummarypodcast.store.ApiKeyCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

@Component
class FtpPublisher(
    private val providerConfigService: UserProviderConfigService,
    private val targetService: PodcastPublicationTargetService,
    private val podcastImageService: PodcastImageService,
    private val feedGenerator: FeedGenerator,
    private val episodeSourcesGenerator: EpisodeSourcesGenerator,
    private val episodeService: EpisodeService,
    private val podcastRepository: PodcastRepository,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper,
    private val appProperties: AppProperties,
    private val connectionFactory: FtpConnectionFactory
) : EpisodePublisher {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val TARGET_NAME = "ftp"
    }

    override fun targetName(): String = TARGET_NAME

    override suspend fun update(episode: Episode, podcast: Podcast, userId: String, externalId: String): PublishResult =
        publish(episode, podcast, userId)

    override fun postPublish(podcast: Podcast, userId: String) {
        val credentials = resolveCredentials(userId)
        val targetConfig = resolveTargetConfig(podcast.id)
        val rawRemotePath = (targetConfig["remotePath"] as? String)?.takeIf { it.isNotBlank() }
        val podcastPath = if (rawRemotePath != null) {
            if (rawRemotePath.endsWith("/")) rawRemotePath else "$rawRemotePath/"
        } else {
            "/${podcast.id}/"
        }
        val publicUrl = (targetConfig["publicUrl"] as? String)?.takeIf { it.isNotBlank() }
            ?.let { if (it.endsWith("/")) it else "$it/" }
        val podcastPublicUrl = publicUrl?.let { "${it}${podcastPath.trimStart('/')}" }

        val user = userRepository.findById(podcast.userId).orElse(null) ?: return
        val baseUrl = podcastPublicUrl ?: appProperties.feed.staticBaseUrl ?: appProperties.feed.baseUrl
        val feedXml = feedGenerator.generate(podcast, user, baseUrl, podcastPublicUrl, publishedTarget = TARGET_NAME)

        val connection = connectionFactory.connectWithFallback(credentials)
        logFallback(connection)
        val ftpClient = connection.client
        try {
            ensureDirectoryExists(ftpClient, podcastPath)
            uploadContent(ftpClient, podcastPath, "feed.xml", feedXml.toByteArray())
            log.info("Uploaded feed.xml for podcast {} to FTP", podcast.id)
        } finally {
            ftpClient.disconnectQuietly()
        }
    }

    override fun unpublish(userId: String, externalId: String) {
        // externalId format: "ftp:slug" — no file path info
        // File deletion handled by deleteRemoteFile() called from service layer
        log.info("FTP unpublish for externalId={}", externalId)
    }

    fun deleteRemoteFile(userId: String, podcastId: String, audioFileName: String) {
        val credentials = resolveCredentials(userId)
        val targetConfig = resolveTargetConfig(podcastId)
        val rawRemotePath = (targetConfig["remotePath"] as? String)?.takeIf { it.isNotBlank() }
        val podcastPath = if (rawRemotePath != null) {
            if (rawRemotePath.endsWith("/")) rawRemotePath else "$rawRemotePath/"
        } else {
            "/$podcastId/"
        }
        val remoteFile = "${podcastPath}episodes/$audioFileName"

        // Deletion is a control-channel command, so it needs no data-channel probe or mode fallback.
        val ftpClient = connectionFactory.connect(credentials)
        try {
            if (ftpClient.deleteFile(remoteFile)) {
                log.info("Deleted FTP file {}", remoteFile)
            } else {
                log.warn("Failed to delete FTP file {} (may not exist): {}", remoteFile, ftpClient.replyString)
            }
        } finally {
            ftpClient.disconnectQuietly()
        }
    }

    override suspend fun publish(episode: Episode, podcast: Podcast, userId: String): PublishResult = withContext(Dispatchers.IO) {
        val credentials = resolveCredentials(userId)
        val targetConfig = resolveTargetConfig(podcast.id)
        val rawRemotePath = (targetConfig["remotePath"] as? String)?.takeIf { it.isNotBlank() }
        val podcastPath = if (rawRemotePath != null) {
            if (rawRemotePath.endsWith("/")) rawRemotePath else "$rawRemotePath/"
        } else {
            "/${podcast.id}/"
        }
        val publicUrl = (targetConfig["publicUrl"] as? String)?.takeIf { it.isNotBlank() }
            ?.let { if (it.endsWith("/")) it else "$it/" }

        val connection = connectionFactory.connectWithFallback(credentials)
        logFallback(connection)
        val ftpClient = connection.client
        try {
            ensureDirectoryExists(ftpClient, podcastPath)
            val remoteEpisodesPath = "${podcastPath}episodes/"
            ensureDirectoryExists(ftpClient, remoteEpisodesPath)

            // Upload sources.html to episodes/
            val articles = episodeService.findArticlesWithTopicsForEpisode(episode.id!!)
            val sourcesPath = episodeSourcesGenerator.generate(episode, podcast, articles)
            if (sourcesPath != null) {
                uploadFile(ftpClient, remoteEpisodesPath, sourcesPath)
                log.info("Uploaded sources.md for episode {} to FTP", episode.id)
            }

            // Upload MP3 to episodes/
            val audioPath = episode.audioFilePath?.let { Path.of(it) }
            if (audioPath != null && Files.exists(audioPath)) {
                uploadFile(ftpClient, remoteEpisodesPath, audioPath)
                log.info("Uploaded MP3 for episode {} to FTP", episode.id)
            }

            // Upload podcast image to podcast root
            val imagePath = podcastImageService.get(podcast.id)
            if (imagePath != null) {
                uploadFile(ftpClient, podcastPath, imagePath)
                log.info("Uploaded podcast image for podcast {} to FTP", podcast.id)
            }

            val slug = episodeSourcesGenerator.deriveSlug(episode)
            val audioFileName = Path.of(episode.audioFilePath!!).fileName
            val podcastPublicUrl = publicUrl?.let { "${it}${podcastPath.trimStart('/')}" }
            val externalUrl = if (podcastPublicUrl != null) {
                "${podcastPublicUrl}episodes/$audioFileName"
            } else {
                "ftp://${credentials.host}${remoteEpisodesPath}$audioFileName"
            }

            PublishResult(externalId = "ftp:$slug", externalUrl = externalUrl)
        } finally {
            ftpClient.disconnectQuietly()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveCredentials(userId: String): FtpConnectionSettings {
        val config = providerConfigService.resolveConfig(userId, ApiKeyCategory.PUBLISHING, TARGET_NAME)
            ?: throw IllegalStateException("No FTP credentials configured. Add FTP credentials in publishing settings.")
        val json = config.apiKey ?: throw IllegalStateException("FTP credentials are incomplete")
        val map = objectMapper.readValue(json, Map::class.java) as Map<String, Any>
        return FtpConnectionSettings(
            host = map["host"] as? String ?: throw IllegalStateException("FTP host is required"),
            port = (map["port"] as? Number)?.toInt() ?: 21,
            username = map["username"] as? String ?: throw IllegalStateException("FTP username is required"),
            password = map["password"] as? String ?: throw IllegalStateException("FTP password is required"),
            useTls = map["useTls"] as? Boolean ?: true,
            // Credentials stored before the mode was configurable have no field: passive, as before.
            transferMode = FtpTransferMode.from(map["transferMode"] as? String)
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveTargetConfig(podcastId: String): Map<String, Any> {
        val target = targetService.get(podcastId, TARGET_NAME)
            ?: throw IllegalStateException("FTP target not configured for this podcast")
        if (!target.enabled) throw IllegalStateException("FTP target is disabled for this podcast")
        return objectMapper.readValue(target.config, Map::class.java) as Map<String, Any>
    }

    /** Publishing still works after a fallback, but the stored setting is wrong and should be corrected. */
    private fun logFallback(connection: FtpConnection) {
        if (connection.fellBackFrom == null) return
        log.warn(
            "FTP fell back from {} to {} mode ({}). Update the transfer mode in publishing settings.",
            connection.fellBackFrom, connection.transferMode, connection.fallbackReason
        )
    }

    private fun ensureDirectoryExists(ftpClient: FTPClient, remotePath: String) {
        val parts = remotePath.split("/").filter { it.isNotEmpty() }
        var current = ""
        for (part in parts) {
            current += "/$part"
            ftpClient.makeDirectory(current)
        }
    }

    private fun uploadFile(ftpClient: FTPClient, remotePath: String, localPath: Path) {
        Files.newInputStream(localPath).use { input ->
            store(ftpClient, "$remotePath${localPath.fileName}", input)
        }
    }

    private fun uploadContent(ftpClient: FTPClient, remotePath: String, fileName: String, content: ByteArray) {
        ByteArrayInputStream(content).use { input ->
            store(ftpClient, "$remotePath$fileName", input)
        }
    }

    /** The server's reply is the only clue to why a store failed (permissions, quota, 522, 425). */
    private fun store(ftpClient: FTPClient, remoteFile: String, input: InputStream) {
        if (!ftpClient.storeFile(remoteFile, input)) {
            throw FtpConnectionException(
                FtpPhase.DATA_CHANNEL,
                "Failed to upload $remoteFile to FTP: ${ftpClient.replyString?.trim()}"
            )
        }
    }
}
