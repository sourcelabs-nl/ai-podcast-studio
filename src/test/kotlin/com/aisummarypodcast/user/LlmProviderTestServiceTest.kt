package com.aisummarypodcast.user

import com.aisummarypodcast.store.ApiKeyCategory
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.web.client.ExpectedCount.never
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class LlmProviderTestServiceTest {

    private val restClientBuilder = RestClient.builder()
    private val mockServer = MockRestServiceServer.bindTo(restClientBuilder).build()
    private val providerConfigService = mockk<UserProviderConfigService>()
    private val service = LlmProviderTestService(providerConfigService, restClientBuilder)
    private val userId = "user-1"

    private fun givenConfig(provider: String, config: ProviderConfig?) {
        every { providerConfigService.resolveConfig(userId, ApiKeyCategory.LLM, provider) } returns config
    }

    @Test
    fun `openrouter is checked on the key endpoint with the stored key`() {
        givenConfig("openrouter", ProviderConfig("https://openrouter.ai/api", "sk-or-test"))
        mockServer.expect(requestTo("https://openrouter.ai/api/v1/key"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer sk-or-test"))
            .andRespond(withSuccess())

        val result = service.test(userId, "openrouter")

        assertTrue(result.success)
        mockServer.verify()
    }

    @Test
    fun `other providers are checked on the models endpoint`() {
        givenConfig("openai", ProviderConfig("https://api.openai.com", "sk-test"))
        mockServer.expect(requestTo("https://api.openai.com/v1/models")).andRespond(withSuccess())

        assertTrue(service.test(userId, "openai").success)
        mockServer.verify()
    }

    @Test
    fun `a rejected key fails with the status`() {
        givenConfig("openrouter", ProviderConfig("https://openrouter.ai/api", "sk-or-bad"))
        mockServer.expect(requestTo("https://openrouter.ai/api/v1/key")).andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        val result = service.test(userId, "openrouter")

        assertFalse(result.success)
        assertEquals("'openrouter' answered HTTP 401", result.message)
    }

    @Test
    fun `a server error fails with the status`() {
        givenConfig("openai", ProviderConfig("https://api.openai.com", "sk-test"))
        mockServer.expect(requestTo("https://api.openai.com/v1/models")).andRespond(withStatus(HttpStatus.BAD_GATEWAY))

        val result = service.test(userId, "openai")

        assertFalse(result.success)
        assertEquals("'openai' answered HTTP 502", result.message)
    }

    @Test
    fun `an empty stored key fails without a request`() {
        givenConfig("openrouter", ProviderConfig("https://openrouter.ai/api", ""))
        mockServer.expect(never(), requestTo("https://openrouter.ai/api/v1/key"))

        val result = service.test(userId, "openrouter")

        assertFalse(result.success)
        assertEquals("The stored API key for 'openrouter' is empty", result.message)
        mockServer.verify()
    }

    @Test
    fun `no config fails without a request`() {
        givenConfig("openrouter", null)

        val result = service.test(userId, "openrouter")

        assertFalse(result.success)
        assertEquals("No config for 'openrouter' and no environment fallback", result.message)
    }

    @Test
    fun `ollama is tested without a key`() {
        givenConfig("ollama", ProviderConfig("http://localhost:11434/v1", null))
        mockServer.expect(requestTo("http://localhost:11434/v1/models"))
            .andExpect(headerDoesNotExist("Authorization"))
            .andRespond(withSuccess())

        assertTrue(service.test(userId, "ollama").success)
        mockServer.verify()
    }
}
