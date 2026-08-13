package com.aisummarypodcast.tts

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

/** MP3 encoding parameters of a TTS chunk. */
data class AudioFormat(
    val sampleRateHertz: Int,
    val channels: Int,
    val bitRateBps: Int
) {
    /** FFmpeg channel layout name for `anullsrc`. */
    val channelLayout: String get() = if (channels >= 2) "stereo" else "mono"
}

@Component
class AudioConcatenator {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /** Used when the first chunk cannot be probed. Matches Inworld's MP3 output. */
        internal val FALLBACK_FORMAT = AudioFormat(sampleRateHertz = 48000, channels = 1, bitRateBps = 128000)
        private const val SILENCE_SECONDS = "0.5"
    }

    fun concatenate(audioChunks: List<ByteArray>, outputPath: Path): Path {
        Files.createDirectories(outputPath.parent)

        val tempDir = Files.createTempDirectory("tts-chunks")
        val chunkFiles = audioChunks.mapIndexed { index, bytes ->
            val chunkFile = tempDir.resolve("chunk_$index.mp3")
            Files.write(chunkFile, bytes)
            chunkFile
        }

        // The concat demuxer below stream-copies, so a leading silence encoded differently from the
        // speech chunks yields a file with mid-stream format changes that Spotify and other players
        // reject. Match the silence to the chunks rather than assuming a fixed provider format:
        // Inworld returns 48kHz MP3, ElevenLabs 44.1kHz.
        val format = chunkFiles.firstOrNull()?.let { probeFormat(it) } ?: FALLBACK_FORMAT

        val filesToConcat = mutableListOf<Path>()
        val silenceFile = generateSilence(tempDir, format)
        if (silenceFile != null) {
            filesToConcat.add(silenceFile)
        }
        filesToConcat.addAll(chunkFiles)

        val concatList = tempDir.resolve("concat.txt")
        Files.writeString(concatList, filesToConcat.joinToString("\n") { "file '${it.toAbsolutePath()}'" })

        val process = ProcessBuilder(
            "ffmpeg", "-y", "-f", "concat", "-safe", "0",
            "-i", concatList.toAbsolutePath().toString(),
            "-c", "copy",
            outputPath.toAbsolutePath().toString()
        )
            .redirectErrorStream(true)
            .start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val output = process.inputStream.bufferedReader().readText()
            throw RuntimeException("FFmpeg failed with exit code $exitCode: $output")
        }

        filesToConcat.forEach { Files.deleteIfExists(it) }
        Files.deleteIfExists(concatList)
        Files.deleteIfExists(tempDir)

        log.info("Concatenated {} chunks into {} ({})", audioChunks.size, outputPath, format)
        return outputPath
    }

    /** Reads the MP3 encoding parameters of [file], or null when ffprobe cannot determine them. */
    internal fun probeFormat(file: Path): AudioFormat? {
        val process = ProcessBuilder(
            "ffprobe", "-v", "error", "-select_streams", "a:0",
            "-show_entries", "stream=sample_rate,channels,bit_rate",
            "-of", "default=noprint_wrappers=1",
            file.toAbsolutePath().toString()
        )
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        if (process.waitFor() != 0) {
            log.warn("ffprobe failed for {}, falling back to {}", file.fileName, FALLBACK_FORMAT)
            return null
        }

        val fields = output.lineSequence()
            .mapNotNull { line -> line.split("=", limit = 2).takeIf { it.size == 2 } }
            .associate { (key, value) -> key.trim() to value.trim() }

        // bit_rate reads "N/A" for VBR streams, which parses to null and falls back
        val format = AudioFormat(
            sampleRateHertz = fields["sample_rate"]?.toIntOrNull() ?: return probeFailure(file, output),
            channels = fields["channels"]?.toIntOrNull() ?: return probeFailure(file, output),
            bitRateBps = fields["bit_rate"]?.toIntOrNull() ?: return probeFailure(file, output)
        )
        return format
    }

    private fun probeFailure(file: Path, output: String): AudioFormat? {
        log.warn("ffprobe returned unusable format for {} ({}), falling back to {}", file.fileName, output.trim(), FALLBACK_FORMAT)
        return null
    }

    /** Generates a leading silence encoded to match [format], or null when ffmpeg fails. */
    private fun generateSilence(tempDir: Path, format: AudioFormat): Path? {
        val silenceFile = tempDir.resolve("silence.mp3")
        val process = ProcessBuilder(
            "ffmpeg", "-y", "-f", "lavfi",
            "-i", "anullsrc=r=${format.sampleRateHertz}:cl=${format.channelLayout}",
            "-t", SILENCE_SECONDS, "-codec:a", "libmp3lame", "-b:a", "${format.bitRateBps}",
            silenceFile.toAbsolutePath().toString()
        )
            .redirectErrorStream(true)
            .start()

        if (process.waitFor() != 0 || !Files.exists(silenceFile)) {
            log.warn("Failed to generate silence, proceeding without it")
            return null
        }
        return silenceFile
    }
}
