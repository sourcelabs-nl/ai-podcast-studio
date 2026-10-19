package com.aisummarypodcast.user

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.publishing.TestConnectionResult
import com.aisummarypodcast.store.User
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(LlmProviderTestController::class)
class LlmProviderTestControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var userService: UserService

    @MockkBean
    private lateinit var llmProviderTestService: LlmProviderTestService

    @MockkBean(relaxed = true)
    private lateinit var appProperties: AppProperties

    private val userId = "user-1"

    @Test
    fun `returns the test result`() {
        every { userService.findById(userId) } returns User(id = userId, name = "Test User")
        every { llmProviderTestService.test(userId, "openrouter") } returns
            TestConnectionResult(success = false, message = "The stored API key for 'openrouter' is empty")

        mockMvc.perform(post("/users/$userId/api-keys/LLM/openrouter/test"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("The stored API key for 'openrouter' is empty"))
    }

    @Test
    fun `unknown user returns 404`() {
        every { userService.findById(userId) } returns null

        mockMvc.perform(post("/users/$userId/api-keys/LLM/openrouter/test"))
            .andExpect(status().isNotFound)
    }
}
