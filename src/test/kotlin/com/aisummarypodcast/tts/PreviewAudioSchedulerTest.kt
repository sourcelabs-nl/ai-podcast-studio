package com.aisummarypodcast.tts

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class PreviewAudioSchedulerTest {

    private val previewAudioService = mockk<PreviewAudioService>()
    private val scheduler = PreviewAudioScheduler(previewAudioService)

    @Test
    fun `the sweep is delegated to the service`() {
        every { previewAudioService.sweepExpiredAudio() } returns 2

        scheduler.sweep()

        verify { previewAudioService.sweepExpiredAudio() }
    }

    @Test
    fun `a failing sweep does not escape the scheduled task`() {
        every { previewAudioService.sweepExpiredAudio() } throws RuntimeException("disk gone")

        assertDoesNotThrow { scheduler.sweep() }
    }
}
