package com.aisummarypodcast.llm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient

class OpenRouterGenerationClientTest {

    private val restClientBuilder = RestClient.builder()
    private val mockServer = MockRestServiceServer.bindTo(restClientBuilder).build()
    private val client = OpenRouterGenerationClient(restClientBuilder)
    private val lookup = GenerationStatsLookup("https://openrouter.ai/api", "sk-test")
    private val url = "https://openrouter.ai/api/v1/generation?id=gen-1"

    // Trimmed from a live response on 2026-09-24.
    private val payload = """
        {"data": {
          "id": "gen-1", "streamed": true, "latency": 268, "generation_time": 789,
          "native_tokens_completion": 117, "native_tokens_reasoning": 71, "finish_reason": "stop",
          "provider_name": "Together",
          "provider_responses": [
            {"provider_name": "DeepInfra", "status": 503, "latency": 30000, "is_byok": false},
            {"provider_name": "Together", "status": 200, "latency": 268, "is_byok": false}
          ]
        }}
    """.trimIndent()

    @Test
    fun `parses the generation stats with the key of the call`() {
        mockServer.expect(requestTo(url))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer sk-test"))
            .andRespond(withSuccess(payload, MediaType.APPLICATION_JSON))

        val stats = client.fetch("gen-1", lookup)!!

        assertEquals(268L, stats.firstContentMs)
        assertEquals(789L, stats.generationTimeMs)
        assertEquals(117, stats.nativeCompletionTokens)
        assertEquals(71, stats.nativeReasoningTokens)
        assertEquals("stop", stats.finishReason)
        assertEquals("Together", stats.servedProvider)
        assertEquals(
            listOf(ProviderAttempt("DeepInfra", 503, 30000), ProviderAttempt("Together", 200, 268)),
            stats.attempts
        )
        mockServer.verify()
    }

    @Test
    fun `a generation not yet accounted for is null`() {
        mockServer.expect(requestTo(url)).andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertNull(client.fetch("gen-1", lookup))
    }

    @Test
    fun `any other failure is thrown`() {
        mockServer.expect(requestTo(url)).andRespond(withStatus(HttpStatus.BAD_GATEWAY))

        assertThrows<HttpServerErrorException> { client.fetch("gen-1", lookup) }
    }
}
