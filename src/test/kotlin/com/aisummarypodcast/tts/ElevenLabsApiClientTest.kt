package com.aisummarypodcast.tts

import com.aisummarypodcast.store.ApiKeyCategory
import com.aisummarypodcast.user.ProviderConfig
import com.aisummarypodcast.user.UserProviderConfigService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.*
import org.springframework.web.client.RestClient

class ElevenLabsApiClientTest {

    private val providerConfigService = mockk<UserProviderConfigService>()
    private val restClientBuilder = RestClient.builder()
    private lateinit var mockServer: MockRestServiceServer
    private lateinit var apiClient: ElevenLabsApiClient

    @BeforeEach
    fun setup() {
        // Construct the client first: its init block sets a timeout request factory on the
        // builder. bindTo then overrides that with the mock factory so requests are intercepted.
        apiClient = ElevenLabsApiClient(providerConfigService, restClientBuilder)
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build()

        every {
            providerConfigService.resolveConfig("u1", ApiKeyCategory.TTS, "elevenlabs")
        } returns ProviderConfig("http://localhost", "test-api-key")
    }

    @AfterEach
    fun reset() {
        mockServer.reset()
    }

    @Test
    fun `textToSpeech sends correct request and returns audio bytes`() {
        mockServer.expect(requestTo("http://localhost/v1/text-to-speech/voice123?output_format=mp3_44100_128"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("xi-api-key", "test-api-key"))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andRespond(withSuccess(byteArrayOf(1, 2, 3), MediaType.APPLICATION_OCTET_STREAM))

        val result = apiClient.textToSpeech("u1", "voice123", "Hello world", null)

        assertEquals(3, result.size)
        mockServer.verify()
    }

    @Test
    fun `textToDialogue sends inputs array`() {
        mockServer.expect(requestTo("http://localhost/v1/text-to-dialogue?output_format=mp3_44100_128"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("xi-api-key", "test-api-key"))
            .andRespond(withSuccess(byteArrayOf(4, 5, 6), MediaType.APPLICATION_OCTET_STREAM))

        val inputs = listOf(
            DialogueInput("Hello!", "v1"),
            DialogueInput("Hi there!", "v2")
        )
        val result = apiClient.textToDialogue("u1", inputs, null)

        assertEquals(3, result.size)
        mockServer.verify()
    }

    @Test
    fun `listVoices parses voice response`() {
        val responseJson = """
        {
            "voices": [
                {"voice_id": "v1", "name": "Rachel", "category": "premade", "preview_url": "https://example.com/rachel.mp3"},
                {"voice_id": "v2", "name": "Adam", "category": "cloned", "preview_url": null}
            ]
        }
        """.trimIndent()

        mockServer.expect(requestTo("http://localhost/v1/voices"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON))

        val voices = apiClient.listVoices("u1")

        assertEquals(2, voices.size)
        assertEquals("v1", voices[0].voiceId)
        assertEquals("Rachel", voices[0].name)
        assertEquals("premade", voices[0].category)
        assertEquals("https://example.com/rachel.mp3", voices[0].previewUrl)
        assertEquals("v2", voices[1].voiceId)
        assertEquals(null, voices[1].previewUrl)
        mockServer.verify()
    }

    @Test
    fun `throws on 401 unauthorized`() {
        mockServer.expect(requestTo("http://localhost/v1/text-to-speech/v1?output_format=mp3_44100_128"))
            .andRespond(withUnauthorizedRequest().body("Unauthorized"))

        val exception = assertThrows<IllegalStateException> {
            apiClient.textToSpeech("u1", "v1", "Hello", null)
        }
        assertEquals("ElevenLabs API key is invalid or expired", exception.message)
    }

    @Test
    fun `throws on 429 rate limit`() {
        mockServer.expect(requestTo("http://localhost/v1/text-to-speech/v1?output_format=mp3_44100_128"))
            .andRespond(withTooManyRequests().body("Rate limited"))

        val exception = assertThrows<IllegalStateException> {
            apiClient.textToSpeech("u1", "v1", "Hello", null)
        }
        assertEquals("ElevenLabs rate limit exceeded. Please try again later.", exception.message)
    }

    @Test
    fun `throws when no provider config found`() {
        every {
            providerConfigService.resolveConfig("u1", ApiKeyCategory.TTS, "elevenlabs")
        } returns null

        val exception = assertThrows<IllegalStateException> {
            apiClient.textToSpeech("u1", "v1", "Hello", null)
        }
        assertEquals("No ElevenLabs provider config found. Configure an ElevenLabs API key for TTS.", exception.message)
    }
}
