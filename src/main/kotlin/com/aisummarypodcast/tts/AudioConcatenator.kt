package com.aisummarypodcast.tts

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

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

        /** Common streaming/podcast loudness target. */
        internal const val TARGET_LUFS = -16.0

        /** True peak ceiling, as an amplitude for ffmpeg's `alimiter`. */
        private const val PEAK_CEILING_DBFS = -1.0
        private val PEAK_CEILING_AMPLITUDE = 10.0.pow(PEAK_CEILING_DBFS / 20.0)

        /** Corrections below this are inaudible and not worth a second encode. */
        private const val NEGLIGIBLE_GAIN_DB = 0.1

        /**
         * Below this, audio is silence or near-silence rather than a quiet voice: ffmpeg reports
         * digital silence as -70 LUFS, which would otherwise be "corrected" by a 54 dB boost that
         * lifts nothing but the noise floor.
         */
        private const val SILENCE_FLOOR_LUFS = -60.0
    }

    fun concatenate(audioChunks: List<AudioChunk>, outputPath: Path): Path {
        Files.createDirectories(outputPath.parent)

        val tempDir = Files.createTempDirectory("tts-chunks")
        try {
            assemble(audioChunks, outputPath, tempDir)
        } finally {
            // ffmpeg writes the chunks, every re-encode and the silence in here, so a failed run
            // would otherwise leave all of it behind.
            deleteRecursively(tempDir)
        }
        return outputPath
    }

    private fun assemble(audioChunks: List<AudioChunk>, outputPath: Path, tempDir: Path) {
        val chunkFiles = audioChunks.mapIndexed { index, chunk ->
            val chunkFile = tempDir.resolve("chunk_$index.mp3")
            Files.write(chunkFile, chunk.bytes)
            chunkFile
        }

        // Normalization re-encodes every chunk to this format, so the silence and the speech share
        // one encoding and the output has no mid-stream format change (which some players reject).
        // The format follows the provider rather than a constant: Inworld returns 48kHz, ElevenLabs
        // 44.1kHz, and resampling either would be pointless work.
        val format = chunkFiles.firstOrNull()?.let { probeFormat(it) } ?: FALLBACK_FORMAT

        val filesToConcat = mutableListOf<Path>()
        generateSilence(tempDir, format)?.let { filesToConcat.add(it) }
        filesToConcat.addAll(normalizeByVoice(audioChunks, chunkFiles, tempDir, format))

        val concatList = tempDir.resolve("concat.txt")
        Files.writeString(concatList, concatFileList(filesToConcat))

        runFfmpeg(
            listOf(
                "ffmpeg", "-y", "-f", "concat", "-safe", "0",
                "-i", concatList.toAbsolutePath().toString(),
                "-c", "copy",
                outputPath.toAbsolutePath().toString()
            )
        ) { output -> throw RuntimeException("FFmpeg failed: $output") }

        log.info("Concatenated {} chunks into {} ({})", audioChunks.size, outputPath, format)
    }

    /**
     * Brings every voice to [TARGET_LUFS] and returns the corrected files, in the original order.
     *
     * The correction is one gain per voice, measured over all of that voice's chunks at once. Per
     * chunk or per turn would also flatten differences the script intends — a deliberately quiet
     * line, a long turn that builds — whereas the problem being corrected is a near-constant offset
     * between voices: measured across six turns of episode 141, one cloned voice sat 4.8 LU below
     * the voice it was answering, consistently across its turns.
     *
     * Gain alone overshoots full scale on a voice that needs a large boost, so each pass limits the
     * peaks. Limiting costs loudness by an amount that depends on how peaky the voice is and is not
     * predictable up front, hence the second measure-and-correct pass.
     */
    private fun normalizeByVoice(
        audioChunks: List<AudioChunk>,
        chunkFiles: List<Path>,
        tempDir: Path,
        format: AudioFormat
    ): List<Path> {
        val byVoice = chunkFiles.indices.groupBy { audioChunks[it].voiceId }
        val corrected = chunkFiles.toMutableList()

        byVoice.forEach { (voiceId, indices) ->
            var files = indices.map { corrected[it] }
            var totalGain = 0.0
            repeat(2) { pass ->
                val gain = gainFor(voiceId, files, tempDir, totalGain) ?: return@forEach
                if (abs(gain) < NEGLIGIBLE_GAIN_DB) return@repeat
                files = files.mapIndexed { i, file ->
                    val target = tempDir.resolve("voice_${voiceId.hashCode()}_p${pass}_$i.mp3")
                    applyGain(file, target, gain, format) ?: run {
                        log.warn("Could not apply gain to voice '{}'; keeping it at {} dB", voiceId, formatDecimal(totalGain))
                        return@forEach
                    }
                }
                totalGain += gain
                // Committed per pass: an early return from a later pass must not discard a
                // correction that already succeeded.
                indices.forEachIndexed { i, chunkIndex -> corrected[chunkIndex] = files[i] }
            }
            log.info("Levelled voice '{}' by {} dB across {} chunk(s)", voiceId, formatDecimal(totalGain), indices.size)
        }
        return corrected
    }

    /** The correction [files] still need to reach [TARGET_LUFS], or null when they must be left alone. */
    private fun gainFor(voiceId: String, files: List<Path>, tempDir: Path, gainSoFar: Double): Double? {
        val measured = measureLoudness(files, tempDir)
        if (measured == null) {
            log.warn("Could not measure loudness of voice '{}'; keeping it at {} dB", voiceId, formatDecimal(gainSoFar))
            return null
        }
        if (measured < SILENCE_FLOOR_LUFS) {
            log.warn("Voice '{}' measured {} LUFS, which is silence; leaving it uncorrected", voiceId, measured)
            return null
        }
        return TARGET_LUFS - measured
    }

    /**
     * Integrated loudness (EBU R128) over [files] played back to back, or null when ffmpeg cannot
     * produce it. Measuring the concatenation rather than averaging per-file values is deliberate:
     * integrated loudness gates on level and weights by duration, so an average over a two-second
     * chunk and a thirty-second one is not the loudness of the two played in sequence.
     */
    private fun measureLoudness(files: List<Path>, tempDir: Path): Double? {
        if (files.isEmpty()) return null
        val listFile = tempDir.resolve("measure_${System.nanoTime()}.txt")
        Files.writeString(listFile, concatFileList(files))

        val output = runFfmpeg(
            listOf(
                "ffmpeg", "-hide_banner", "-nostats",
                "-f", "concat", "-safe", "0", "-i", listFile.toAbsolutePath().toString(),
                "-af", "ebur128=framelog=quiet", "-f", "null", "-"
            )
        ) { return null }

        Files.deleteIfExists(listFile)
        // The summary block prints "    I:         -21.4 LUFS" after the per-frame log.
        return Regex("""I:\s+(-?\d+(?:\.\d+)?) LUFS""").findAll(output).lastOrNull()
            ?.groupValues?.get(1)?.toDoubleOrNull()
    }

    /** Applies [gainDb] with peak limiting, re-encoded to [format]; null when ffmpeg fails. */
    private fun applyGain(input: Path, output: Path, gainDb: Double, format: AudioFormat): Path? {
        // ffmpeg needs a decimal point, so the numbers are formatted independently of the default
        // locale — on a Dutch JVM "%.2f" yields "17,00", which ffmpeg does not parse as a number.
        val filter = "volume=${formatDecimal(gainDb)}dB," +
            "alimiter=limit=${formatDecimal(PEAK_CEILING_AMPLITUDE, "%.4f")}:level=disabled"
        runFfmpeg(
            listOf(
                "ffmpeg", "-y", "-loglevel", "error", "-i", input.toAbsolutePath().toString(),
                "-af", filter,
                "-ar", "${format.sampleRateHertz}", "-ac", "${format.channels}",
                "-codec:a", "libmp3lame", "-b:a", "${format.bitRateBps}",
                output.toAbsolutePath().toString()
            )
        ) { return null }
        return output.takeIf { Files.exists(it) }
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

    private fun formatDecimal(value: Double, pattern: String = "%.2f"): String = String.format(Locale.ROOT, pattern, value)

    private fun concatFileList(files: List<Path>): String =
        files.joinToString("\n") { "file '${it.toAbsolutePath()}'" }

    /** Runs [command], returning its combined output; [onFailure] decides what a non-zero exit means. */
    private inline fun runFfmpeg(command: List<String>, onFailure: (String) -> Nothing): String {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        if (process.waitFor() != 0) onFailure(output)
        return output
    }

    private fun deleteRecursively(dir: Path) {
        Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }
}
