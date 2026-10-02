package com.aisummarypodcast.podcast

import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastStyle
import com.aisummarypodcast.store.TtsProviderType
import org.springframework.stereotype.Component
import java.time.ZoneId

/**
 * Checks a fully built [Podcast] before it is persisted. Throws on the first failure, in a fixed
 * order: TTS config, timezone, fullBodyThreshold, composeSettings, subtopics, rapid-fire limits.
 * Language, provider and style are rejected earlier, while the request is mapped.
 */
@Component
class PodcastValidator {

    /** @throws InvalidPodcastException or [InvalidComposeSettingsException] when a rule fails. */
    fun validate(podcast: Podcast) {
        validateTtsConfig(podcast.ttsProvider, podcast.style, podcast.ttsVoices)?.let { throw InvalidPodcastException(it) }
        validateTimezone(podcast.timezone)
        if (podcast.fullBodyThreshold != null && podcast.fullBodyThreshold < 1) {
            throw InvalidPodcastException("fullBodyThreshold must be at least 1")
        }
        validateComposeSettings(podcast.composeSettings)
        validateSubtopics(podcast.subtopics?.weights)
        validateRapidFireThreshold(podcast.rapidFireWeightThreshold)
        validateRapidFireMaxItems(podcast.rapidFireMaxItems)
    }

    private fun validateTtsConfig(ttsProvider: TtsProviderType, style: PodcastStyle, ttsVoices: Map<String, String>?): String? {
        val dialogueProviders = setOf(TtsProviderType.ELEVENLABS, TtsProviderType.INWORLD)
        if (style == PodcastStyle.DIALOGUE && ttsProvider !in dialogueProviders) {
            return "Dialogue style requires ElevenLabs or Inworld as TTS provider"
        }
        if (style == PodcastStyle.DIALOGUE && (ttsVoices == null || ttsVoices.size < 2)) {
            return "Dialogue style requires at least two voice roles in ttsVoices (e.g., host and cohost)"
        }
        if (style == PodcastStyle.INTERVIEW && ttsProvider !in dialogueProviders) {
            return "Interview style requires ElevenLabs or Inworld as TTS provider"
        }
        if (style == PodcastStyle.INTERVIEW && (ttsVoices == null || ttsVoices.size < 2)) {
            return "Interview style requires at least two voice roles in ttsVoices (interviewer and expert)"
        }
        if (style == PodcastStyle.INTERVIEW && ttsVoices != null && ttsVoices.keys != setOf("interviewer", "expert")) {
            return "Interview style requires exactly 'interviewer' and 'expert' voice roles"
        }
        return null
    }

    private fun validateTimezone(timezone: String) {
        try {
            ZoneId.of(timezone)
        } catch (_: Exception) {
            throw InvalidPodcastException("Invalid timezone: $timezone")
        }
    }

    private fun validateComposeSettings(composeSettings: Map<String, String>?) {
        composeSettings?.get("reasoningEffort")?.takeIf { it.isNotBlank() }?.let { effort ->
            if (effort !in REASONING_EFFORTS) {
                throw InvalidComposeSettingsException(
                    "composeSettings.reasoningEffort must be one of ${REASONING_EFFORTS.joinToString()}"
                )
            }
        }
        val temperatureRaw = composeSettings?.get("temperature") ?: return
        val temperature = temperatureRaw.toDoubleOrNull()
            ?: throw InvalidComposeSettingsException("composeSettings.temperature must be a number")
        if (temperature < 0.0 || temperature > 2.0) {
            throw InvalidComposeSettingsException("composeSettings.temperature must be in [0.0, 2.0]")
        }
    }

    private fun validateSubtopics(subtopics: Map<String, Int>?) {
        if (subtopics == null) return
        for ((name, weight) in subtopics) {
            if (name.isBlank()) throw InvalidPodcastException("Subtopic name must not be empty")
            if (weight < 1 || weight > 10) {
                throw InvalidPodcastException("Subtopic '$name' weight must be in [1, 10], got $weight")
            }
        }
    }

    private fun validateRapidFireThreshold(threshold: Int) {
        if (threshold < 0 || threshold > 10) {
            throw InvalidPodcastException("rapidFireWeightThreshold must be in [0, 10], got $threshold")
        }
    }

    private fun validateRapidFireMaxItems(maxItems: Int?) {
        if (maxItems == null) return
        if (maxItems < 0 || maxItems > 50) {
            throw InvalidPodcastException("rapidFireMaxItems must be in [0, 50], got $maxItems")
        }
    }

    private companion object {
        /** Reasoning efforts OpenRouter documents, lowest to highest. */
        val REASONING_EFFORTS = listOf("none", "minimal", "low", "medium", "high", "xhigh", "max")
    }
}
