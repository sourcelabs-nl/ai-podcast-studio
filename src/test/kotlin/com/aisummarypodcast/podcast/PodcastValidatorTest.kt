package com.aisummarypodcast.podcast

import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastStyle
import com.aisummarypodcast.store.Subtopics
import com.aisummarypodcast.store.TtsProviderType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class PodcastValidatorTest {

    private val validator = PodcastValidator()
    private val base = Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech")

    private fun invalid(podcast: Podcast): String =
        assertThrows<InvalidPodcastException> { validator.validate(podcast) }.message!!

    private fun invalidCompose(settings: Map<String, String>): String =
        assertThrows<InvalidComposeSettingsException> { validator.validate(base.copy(composeSettings = settings)) }.message!!

    @Test
    fun `a default podcast is valid`() {
        assertDoesNotThrow { validator.validate(base) }
    }

    @Test
    fun `dialogue style requires ElevenLabs or Inworld`() {
        val podcast = base.copy(style = PodcastStyle.DIALOGUE, ttsVoices = mapOf("host" to "a", "cohost" to "b"))
        assertEquals("Dialogue style requires ElevenLabs or Inworld as TTS provider", invalid(podcast))
    }

    @Test
    fun `dialogue style requires two voices`() {
        val podcast = base.copy(style = PodcastStyle.DIALOGUE, ttsProvider = TtsProviderType.ELEVENLABS, ttsVoices = mapOf("host" to "a"))
        assertEquals("Dialogue style requires at least two voice roles in ttsVoices (e.g., host and cohost)", invalid(podcast))
    }

    @Test
    fun `interview style requires ElevenLabs or Inworld`() {
        val podcast = base.copy(style = PodcastStyle.INTERVIEW, ttsVoices = mapOf("interviewer" to "a", "expert" to "b"))
        assertEquals("Interview style requires ElevenLabs or Inworld as TTS provider", invalid(podcast))
    }

    @Test
    fun `interview style requires two voices`() {
        val podcast = base.copy(style = PodcastStyle.INTERVIEW, ttsProvider = TtsProviderType.INWORLD, ttsVoices = null)
        assertEquals("Interview style requires at least two voice roles in ttsVoices (interviewer and expert)", invalid(podcast))
    }

    @Test
    fun `interview style requires the interviewer and expert roles`() {
        val podcast = base.copy(style = PodcastStyle.INTERVIEW, ttsProvider = TtsProviderType.INWORLD, ttsVoices = mapOf("host" to "a", "cohost" to "b"))
        assertEquals("Interview style requires exactly 'interviewer' and 'expert' voice roles", invalid(podcast))
    }

    @Test
    fun `a valid interview config passes`() {
        val podcast = base.copy(style = PodcastStyle.INTERVIEW, ttsProvider = TtsProviderType.INWORLD, ttsVoices = mapOf("interviewer" to "a", "expert" to "b"))
        assertDoesNotThrow { validator.validate(podcast) }
    }

    @Test
    fun `an unknown timezone is rejected`() {
        assertEquals("Invalid timezone: Mars/Base", invalid(base.copy(timezone = "Mars/Base")))
    }

    @Test
    fun `fullBodyThreshold below 1 is rejected`() {
        assertEquals("fullBodyThreshold must be at least 1", invalid(base.copy(fullBodyThreshold = 0)))
    }

    @Test
    fun `an unknown reasoning effort is rejected with the compose settings exception`() {
        assertEquals(
            "composeSettings.reasoningEffort must be one of none, minimal, low, medium, high, xhigh, max",
            invalidCompose(mapOf("reasoningEffort" to "extreme"))
        )
    }

    @Test
    fun `a documented reasoning effort and a blank one are accepted`() {
        assertDoesNotThrow { validator.validate(base.copy(composeSettings = mapOf("reasoningEffort" to "xhigh"))) }
        assertDoesNotThrow { validator.validate(base.copy(composeSettings = mapOf("reasoningEffort" to " "))) }
    }

    @Test
    fun `a non-numeric temperature is rejected`() {
        assertEquals("composeSettings.temperature must be a number", invalidCompose(mapOf("temperature" to "hot")))
    }

    @Test
    fun `a temperature outside 0 to 2 is rejected`() {
        assertEquals("composeSettings.temperature must be in [0.0, 2.0]", invalidCompose(mapOf("temperature" to "2.5")))
    }

    @Test
    fun `a blank subtopic name is rejected`() {
        assertEquals("Subtopic name must not be empty", invalid(base.copy(subtopics = Subtopics(mapOf(" " to 3)))))
    }

    @Test
    fun `a subtopic weight outside 1 to 10 is rejected`() {
        assertEquals(
            "Subtopic 'ai' weight must be in [1, 10], got 11",
            invalid(base.copy(subtopics = Subtopics(mapOf("ai" to 11))))
        )
    }

    @Test
    fun `rapidFireWeightThreshold outside 0 to 10 is rejected`() {
        assertEquals("rapidFireWeightThreshold must be in [0, 10], got 11", invalid(base.copy(rapidFireWeightThreshold = 11)))
        assertEquals("rapidFireWeightThreshold must be in [0, 10], got -1", invalid(base.copy(rapidFireWeightThreshold = -1)))
    }

    @Test
    fun `rapidFireMaxItems outside 0 to 50 is rejected`() {
        assertEquals("rapidFireMaxItems must be in [0, 50], got 51", invalid(base.copy(rapidFireMaxItems = 51)))
    }

    @Test
    fun `the first failing rule wins in the documented order`() {
        val podcast = base.copy(timezone = "Mars/Base", fullBodyThreshold = 0, rapidFireMaxItems = 99)
        assertEquals("Invalid timezone: Mars/Base", invalid(podcast))
    }
}
