package com.aisummarypodcast.tts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class AudioConcatenatorTest {

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

        concatenator.concatenate(listOf(Files.readAllBytes(chunk)), output)

        assertEquals(AudioFormat(44100, 1, 128000), concatenator.probeFormat(output))
    }

    @Test
    fun `output matches 48kHz chunks`() {
        val chunk = generateTone(sampleRate = 48000, channels = 1, bitRateBps = 128000)
        val output = tempDir.resolve("out/episode.mp3")

        concatenator.concatenate(listOf(Files.readAllBytes(chunk)), output)

        assertEquals(AudioFormat(48000, 1, 128000), concatenator.probeFormat(output))
    }

    @Test
    fun `concatenating multiple chunks preserves a single uniform format`() {
        val bytes = Files.readAllBytes(generateTone(sampleRate = 44100, channels = 1, bitRateBps = 128000))
        val output = tempDir.resolve("out/episode.mp3")

        concatenator.concatenate(listOf(bytes, bytes, bytes), output)

        assertEquals(AudioFormat(44100, 1, 128000), concatenator.probeFormat(output))
    }

    @Test
    fun `falls back to the default format when there are no chunks`() {
        val output = tempDir.resolve("out/episode.mp3")

        concatenator.concatenate(emptyList(), output)

        assertEquals(AudioConcatenator.FALLBACK_FORMAT, concatenator.probeFormat(output))
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
