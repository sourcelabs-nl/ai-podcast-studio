package com.aisummarypodcast.tts

import com.aisummarypodcast.store.ApiKeyCategory
import com.aisummarypodcast.user.ProviderConfig
import com.aisummarypodcast.user.UserProviderConfigService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class InworldApiClientTest {

    private val providerConfigService = mockk<UserProviderConfigService>()

    @Test
    fun `buildBasicToken creates base64-encoded token from credentials`() {
        val client = InworldApiClient(providerConfigService, mockk())
        val token = client.buildBasicToken("my-key:my-secret")

        assertNotNull(token)
        val decoded = String(java.util.Base64.getDecoder().decode(token))
        assertEquals("my-key:my-secret", decoded)
    }

    @Test
    fun `createClient throws when no config available`() {
        every {
            providerConfigService.resolveConfig("u1", ApiKeyCategory.TTS, "inworld")
        } returns null

        val client = InworldApiClient(providerConfigService, mockk())

        assertThrows<IllegalStateException> {
            client.createClient("u1")
        }
    }

    @Test
    fun `createClient throws when apiKey is null`() {
        every {
            providerConfigService.resolveConfig("u1", ApiKeyCategory.TTS, "inworld")
        } returns ProviderConfig(baseUrl = "https://api.inworld.ai", apiKey = null)

        val client = InworldApiClient(providerConfigService, mockk())

        assertThrows<IllegalStateException> {
            client.createClient("u1")
        }
    }

    // --- Request body tests ---

    private fun buildBody(options: InworldSynthesisOptions = InworldSynthesisOptions()): Map<String, Any> =
        InworldApiClient(providerConfigService, mockk())
            .buildSynthesisBody("voice-1", "Hello world", "inworld-tts-2", options)

    @Suppress("UNCHECKED_CAST")
    private fun audioConfigOf(body: Map<String, Any>): Map<String, Any> = body["audioConfig"] as Map<String, Any>

    @Test
    fun `buildSynthesisBody uses the documented bitRate field name`() {
        val audioConfig = audioConfigOf(buildBody())

        assertEquals(128000, audioConfig["bitRate"])
        assertFalse(audioConfig.containsKey("bitRateHertz"), "bitRateHertz is not a documented Inworld field")
        assertEquals("MP3", audioConfig["audioEncoding"])
        assertEquals(48000, audioConfig["sampleRateHertz"])
    }

    @Test
    fun `buildSynthesisBody omits enhanceGeneration when unset`() {
        assertFalse(buildBody().containsKey("enhanceGeneration"))
    }

    @Test
    fun `buildSynthesisBody sends enhanceGeneration when enabled`() {
        assertEquals(true, buildBody(InworldSynthesisOptions(enhanceGeneration = true))["enhanceGeneration"])
    }

    @Test
    fun `buildSynthesisBody sends enhanceGeneration false when explicitly disabled`() {
        assertEquals(false, buildBody(InworldSynthesisOptions(enhanceGeneration = false))["enhanceGeneration"])
    }

    @Test
    fun `buildSynthesisBody sends deliveryMode instead of temperature when both set`() {
        val body = buildBody(InworldSynthesisOptions(temperature = 0.8, deliveryMode = "EXPRESSIVE"))

        assertEquals("EXPRESSIVE", body["deliveryMode"])
        assertFalse(body.containsKey("temperature"), "deliveryMode replaces temperature on TTS-2")
    }

    @Test
    fun `buildSynthesisBody maps speed onto speakingRate`() {
        assertEquals(1.2, audioConfigOf(buildBody(InworldSynthesisOptions(speed = 1.2)))["speakingRate"])
    }
}
