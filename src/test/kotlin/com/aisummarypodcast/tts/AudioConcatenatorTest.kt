package com.aisummarypodcast.tts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class AudioConcatenatorTest {

    private companion object {
        const val TONE_SECONDS = 3.0

        /** The specs allow 0.5 LU; the slack covers mp3 round-tripping of the measured slice. */
        const val TOLERANCE_LU = 0.8
    }

    private val concatenator = AudioConcatenator()

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun requireFfmpeg() {
        assumeTrue(commandExists("ffmpeg") && commandExists("ffprobe"), "ffmpeg/ffprobe not installed")
    }

    @Test
    fun `probeFormat reads sample rate channels and bitrate`() {
        val chunk = generateTone(sampleRate = 44100, channels = 1, bitRateBps = 128000)

        val format = concatenator.probeFormat(chunk)

        assertEquals(AudioFormat(44100, 1, 128000), format)
    }

    @Test
    fun `probeFormat returns null for a file ffprobe cannot read`() {
        val notAudio = tempDir.resolve("garbage.mp3")
        Files.write(notAudio, byteArrayOf(0, 1, 2, 3))

        assertNull(concatenator.probeFormat(notAudio))
    }

    @Test
    fun `output matches 44_1kHz chunks rather than the 48kHz default`() {
        // ElevenLabs returns mp3_44100_128. A hardcoded 48kHz silence would leave the concatenated
        // file with a mid-stream sample rate change, which Spotify rejects.
        val chunk = generateTone(sampleRate = 44100, channels = 1, bitRateBps = 128000)
        val output = tempDir.resolve("out/episode.mp3")

        concatenator.concatenate(listOf(AudioChunk("voice-1", Files.readAllBytes(chunk))), output)

        assertEquals(AudioFormat(44100, 1, 128000), concatenator.probeFormat(output))
    }

    @Test
    fun `output matches 48kHz chunks`() {
        val chunk = generateTone(sampleRate = 48000, channels = 1, bitRateBps = 128000)
        val output = tempDir.resolve("out/episode.mp3")

        concatenator.concatenate(listOf(AudioChunk("voice-1", Files.readAllBytes(chunk))), output)

        assertEquals(AudioFormat(48000, 1, 128000), concatenator.probeFormat(output))
    }

    @Test
    fun `concatenating multiple chunks preserves a single uniform format`() {
        val bytes = Files.readAllBytes(generateTone(sampleRate = 44100, channels = 1, bitRateBps = 128000))
        val output = tempDir.resolve("out/episode.mp3")

        concatenator.concatenate(listOf(bytes, bytes, bytes).map { AudioChunk("voice-1", it) }, output)

        assertEquals(AudioFormat(44100, 1, 128000), concatenator.probeFormat(output))
    }

    @Test
    fun `falls back to the default format when there are no chunks`() {
        val output = tempDir.resolve("out/episode.mp3")

        concatenator.concatenate(emptyList(), output)

        assertEquals(AudioConcatenator.FALLBACK_FORMAT, concatenator.probeFormat(output))
    }

    @Test
    fun `two voices at different levels are matched to the target`() {
        val loud = AudioChunk("interviewer", Files.readAllBytes(generateTone(levelDb = -14.0)))
        val quiet = AudioChunk("expert", Files.readAllBytes(generateTone(levelDb = -28.0)))
        val output = tempDir.resolve("out/episode.mp3")

        concatenator.concatenate(listOf(loud, quiet), output)

        // Segments follow the 0.5s leading silence, one tone each.
        val first = measureSegment(output, from = 0.5, duration = TONE_SECONDS)
        val second = measureSegment(output, from = 0.5 + TONE_SECONDS, duration = TONE_SECONDS)
        assertEquals(AudioConcatenator.TARGET_LUFS, first, TOLERANCE_LU)
        assertEquals(AudioConcatenator.TARGET_LUFS, second, TOLERANCE_LU)
    }

    @Test
    fun `differences within one voice survive the correction`() {
        // Both chunks belong to one speaker, 8 dB apart: a deliberately quieter line must stay
        // quieter afterwards, which is what per-voice correction buys over per-chunk levelling.
        val strong = AudioChunk("expert", Files.readAllBytes(generateTone(levelDb = -14.0)))
        val soft = AudioChunk("expert", Files.readAllBytes(generateTone(levelDb = -22.0)))
        val output = tempDir.resolve("out/episode.mp3")

        concatenator.concatenate(listOf(strong, soft), output)

        val first = measureSegment(output, from = 0.5, duration = TONE_SECONDS)
        val second = measureSegment(output, from = 0.5 + TONE_SECONDS, duration = TONE_SECONDS)
        assertEquals(8.0, first - second, 1.0)
    }

    @Test
    fun `a single voice is corrected to the target`() {
        val chunk = AudioChunk("narrator", Files.readAllBytes(generateTone(levelDb = -30.0)))
        val output = tempDir.resolve("out/episode.mp3")

        concatenator.concatenate(listOf(chunk), output)

        assertEquals(AudioConcatenator.TARGET_LUFS, measureSegment(output, from = 0.5, duration = TONE_SECONDS), TOLERANCE_LU)
    }

    @Test
    fun `peaks stay below the ceiling when a quiet voice is boosted`() {
        val chunk = AudioChunk("expert", Files.readAllBytes(generateTone(levelDb = -35.0)))
        val output = tempDir.resolve("out/episode.mp3")

        concatenator.concatenate(listOf(chunk), output)

        assertTrue(truePeakDbfs(output) <= -1.0 + 0.3, "true peak was ${truePeakDbfs(output)} dBFS")
    }

    @Test
    fun `silent chunks are left alone rather than boosted`() {
        val silent = AudioChunk("expert", Files.readAllBytes(generateSilentChunk()))
        val output = tempDir.resolve("out/episode.mp3")

        concatenator.concatenate(listOf(silent), output)

        // A 54 dB "correction" toward the target would lift nothing but the noise floor.
        assertTrue(measureSegment(output, from = 0.5, duration = TONE_SECONDS) < -60.0)
    }

    /** Integrated loudness of the slice of [file] starting at [from], in seconds. */
    private fun measureSegment(file: Path, from: Double, duration: Double): Double {
        val output = runFfmpeg(
            "ffmpeg", "-hide_banner", "-nostats", "-ss", "$from", "-t", "$duration",
            "-i", file.toAbsolutePath().toString(), "-af", "ebur128=framelog=quiet", "-f", "null", "-"
        )
        return Regex("""I:\s+(-?\d+(?:\.\d+)?) LUFS""").findAll(output).last().groupValues[1].toDouble()
    }

    private fun truePeakDbfs(file: Path): Double {
        val output = runFfmpeg(
            "ffmpeg", "-hide_banner", "-nostats", "-i", file.toAbsolutePath().toString(),
            "-af", "ebur128=peak=true:framelog=quiet", "-f", "null", "-"
        )
        return Regex("""Peak:\s+(-?\d+(?:\.\d+)?) dBFS""").findAll(output).last().groupValues[1].toDouble()
    }

    private fun runFfmpeg(vararg command: String): String {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "ffmpeg failed: $output" }
        return output
    }

    /** A tone at a known integrated loudness, standing in for one speaker's chunk. */
    private fun generateTone(levelDb: Double): Path {
        val file = tempDir.resolve("tone-$levelDb.mp3")
        runFfmpeg(
            "ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi",
            "-i", "sine=frequency=440:sample_rate=48000:duration=$TONE_SECONDS",
            "-af", "volume=${levelDb}dB", "-ac", "1",
            "-codec:a", "libmp3lame", "-b:a", "128000",
            file.toAbsolutePath().toString()
        )
        return file
    }

    private fun generateSilentChunk(): Path {
        val file = tempDir.resolve("silent.mp3")
        runFfmpeg(
            "ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi",
            "-i", "anullsrc=r=48000:cl=mono", "-t", "$TONE_SECONDS",
            "-codec:a", "libmp3lame", "-b:a", "128000",
            file.toAbsolutePath().toString()
        )
        return file
    }

    private fun generateTone(sampleRate: Int, channels: Int, bitRateBps: Int): Path {
        val file = tempDir.resolve("tone-$sampleRate-$channels-$bitRateBps.mp3")
        val layout = if (channels >= 2) "stereo" else "mono"
        val process = ProcessBuilder(
            "ffmpeg", "-y", "-f", "lavfi",
            "-i", "sine=frequency=440:sample_rate=$sampleRate:duration=1",
            "-ac", "$channels", "-channel_layout", layout,
            "-codec:a", "libmp3lame", "-b:a", "$bitRateBps",
            file.toAbsolutePath().toString()
        )
            .redirectErrorStream(true)
            .start()
        check(process.waitFor() == 0) { "ffmpeg failed: ${process.inputStream.bufferedReader().readText()}" }
        return file
    }

    private fun commandExists(command: String): Boolean = runCatching {
        ProcessBuilder(command, "-version").redirectErrorStream(true).start().waitFor() == 0
    }.getOrDefault(false)
}
