package com.aisummarypodcast.tts

import com.aisummarypodcast.api.InvalidRequestException
import com.aisummarypodcast.api.UpstreamServiceException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VoiceDiscoveryServiceTest {

    private val elevenLabsApiClient = mockk<ElevenLabsApiClient>()
    private val inworldApiClient = mockk<InworldApiClient>()
    private val service = VoiceDiscoveryService(elevenLabsApiClient, inworldApiClient)

    private val voice = VoiceInfo("v1", "Rachel", "premade", null)

    @Test
    fun `lists ElevenLabs voices through the ElevenLabs client`() {
        every { elevenLabsApiClient.listVoices("u1") } returns listOf(voice)

        assertEquals(listOf(voice), service.listVoices("u1", "elevenlabs"))
    }

    @Test
    fun `lists Inworld voices through the Inworld client`() {
        every { inworldApiClient.listVoices("u1") } returns listOf(voice)

        assertEquals(listOf(voice), service.listVoices("u1", "inworld"))
    }

    @Test
    fun `an unsupported provider is an invalid request`() {
        val ex = assertThrows<InvalidRequestException> { service.listVoices("u1", "openai") }

        assertEquals("Voice discovery is not supported for the 'openai' provider", ex.message)
    }

    @Test
    fun `a provider that is not configured is an invalid request with the client message`() {
        every { elevenLabsApiClient.listVoices("u1") } throws IllegalStateException("No ElevenLabs provider config found.")

        val ex = assertThrows<InvalidRequestException> { service.listVoices("u1", "elevenlabs") }

        assertEquals("No ElevenLabs provider config found.", ex.message)
    }

    @Test
    fun `an ElevenLabs failure is an upstream failure`() {
        every { elevenLabsApiClient.listVoices("u1") } throws RuntimeException("Connection refused")

        val ex = assertThrows<UpstreamServiceException> { service.listVoices("u1", "elevenlabs") }

        assertEquals("Failed to fetch voices from ElevenLabs: Connection refused", ex.message)
    }

    @Test
    fun `an Inworld failure is an upstream failure`() {
        every { inworldApiClient.listVoices("u1") } throws RuntimeException("Connection refused")

        val ex = assertThrows<UpstreamServiceException> { service.listVoices("u1", "inworld") }

        assertEquals("Failed to fetch voices from Inworld: Connection refused", ex.message)
    }
}
