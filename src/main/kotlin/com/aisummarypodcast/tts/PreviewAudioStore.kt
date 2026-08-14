package com.aisummarypodcast.tts

import com.aisummarypodcast.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.io.path.extension
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries

/**
 * Filesystem home for preview audio. A preview file belongs to no episode and nothing records it in
 * the database, so the podcast that owns it is expressed by the directory it lives in: a file is
 * only reachable through the podcast whose directory holds it, and the caller has already been
 * checked to own that podcast. The identifier itself is a random UUID, so it cannot be guessed, and
 * anything that is not a UUID is refused before it reaches the filesystem.
 */
@Component
class PreviewAudioStore(
    private val appProperties: AppProperties,
    private val audioConcatenator: AudioConcatenator
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private companion object {
        const val AUDIO_EXTENSION = "mp3"
    }

    private val root: Path get() = Path.of(appProperties.previewAudio.directory).toAbsolutePath().normalize()

    /** Writes [audioChunks] as one MP3 and returns the opaque id the file is reachable under. */
    fun write(podcastId: String, audioChunks: List<ByteArray>, requiresConcatenation: Boolean): String {
        require(audioChunks.isNotEmpty()) { "Cannot store preview audio without any audio chunks" }
        val audioId = UUID.randomUUID().toString()
        val target = podcastDirectory(podcastId).resolve("$audioId.$AUDIO_EXTENSION")
        Files.createDirectories(target.parent)

        if (requiresConcatenation) {
            audioConcatenator.concatenate(audioChunks, target)
        } else {
            Files.write(target, audioChunks.first())
        }
        log.info("Stored preview audio {} for podcast {} ({} bytes)", audioId, podcastId, Files.size(target))
        return audioId
    }

    /** The stored file, or null when [audioId] is malformed, unknown, or not this podcast's. */
    fun find(podcastId: String, audioId: String): Path? {
        if (!isValidAudioId(audioId)) {
            log.warn("Rejected malformed preview audio id '{}'", audioId)
            return null
        }
        val target = podcastDirectory(podcastId).resolve("$audioId.$AUDIO_EXTENSION").normalize()
        if (!target.startsWith(root)) return null
        return target.takeIf { it.isRegularFile() }
    }

    /** Deletes preview audio older than the configured retention and returns how many files went. */
    fun sweepExpired(): Int {
        if (!root.isDirectory()) return 0
        val cutoff = Instant.now().minus(Duration.ofMinutes(appProperties.previewAudio.retentionMinutes))
        var deleted = 0
        for (podcastDirectory in root.listDirectoryEntries().filter { it.isDirectory() }) {
            for (file in podcastDirectory.listDirectoryEntries().filter { it.isRegularFile() }) {
                if (file.extension != AUDIO_EXTENSION) continue
                if (file.getLastModifiedTime().toInstant().isAfter(cutoff)) continue
                if (Files.deleteIfExists(file)) deleted++
            }
            if (podcastDirectory.listDirectoryEntries().isEmpty()) Files.deleteIfExists(podcastDirectory)
        }
        if (deleted > 0) log.info("Swept {} expired preview audio file(s)", deleted)
        return deleted
    }

    private fun podcastDirectory(podcastId: String): Path = root.resolve(podcastId)

    private fun isValidAudioId(audioId: String): Boolean =
        runCatching { UUID.fromString(audioId).toString() == audioId }.getOrDefault(false)
}
