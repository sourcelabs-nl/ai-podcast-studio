package com.aisummarypodcast.tts

import com.aisummarypodcast.api.InvalidRequestException
import com.aisummarypodcast.api.UpstreamServiceException
import com.aisummarypodcast.store.TtsProviderType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** Lists the voices a user's TTS provider offers, hiding which API client answers for which provider. */
@Service
class VoiceDiscoveryService(
    private val elevenLabsApiClient: ElevenLabsApiClient,
    private val inworldApiClient: InworldApiClient
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * @throws InvalidRequestException when the provider is unsupported or not configured for the user
     * @throws UpstreamServiceException when the provider's API fails
     */
    fun listVoices(userId: String, provider: String): List<VoiceInfo> =
        when (provider) {
            TtsProviderType.ELEVENLABS.value -> fetchVoices("ElevenLabs") { elevenLabsApiClient.listVoices(userId) }
            TtsProviderType.INWORLD.value -> fetchVoices("Inworld") { inworldApiClient.listVoices(userId) }
            else -> throw InvalidRequestException("Voice discovery is not supported for the '$provider' provider")
        }

    private fun fetchVoices(providerName: String, fetch: () -> List<VoiceInfo>): List<VoiceInfo> =
        try {
            fetch()
        } catch (e: IllegalStateException) {
            throw InvalidRequestException(e.message.orEmpty())
        } catch (e: Exception) {
            log.error("Failed to fetch voices from {}: {}", providerName, e.message, e)
            throw UpstreamServiceException("Failed to fetch voices from $providerName: ${e.message}", e)
        }
}
