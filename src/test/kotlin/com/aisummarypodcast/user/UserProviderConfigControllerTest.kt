package com.aisummarypodcast.user

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.store.ApiKeyCategory
import com.aisummarypodcast.store.User
import com.aisummarypodcast.store.UserProviderConfig
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(UserProviderConfigController::class)
class UserProviderConfigControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var providerConfigService: UserProviderConfigService

    @MockkBean
    private lateinit var userService: UserService

    @MockkBean(relaxed = true)
    private lateinit var appProperties: AppProperties

    private val userId = "user-1"
    private val user = User(id = userId, name = "Test User")

    @Test
    fun `PUT with provider and apiKey sets config`() {
        every { userService.findById(userId) } returns user
        every { providerConfigService.hasDefaultUrl("openrouter") } returns true
        every { providerConfigService.setConfig(userId, ApiKeyCategory.LLM, "openrouter", "sk-123", null) } returns Unit

        mockMvc.perform(
            put("/users/$userId/api-keys/LLM")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider":"openrouter","apiKey":"sk-123"}""")
        ).andExpect(status().isOk)

        verify { providerConfigService.setConfig(userId, ApiKeyCategory.LLM, "openrouter", "sk-123", null) }
    }

    @Test
    fun `PUT with provider only and no apiKey sets config for Ollama`() {
        every { userService.findById(userId) } returns user
        every { providerConfigService.hasDefaultUrl("ollama") } returns true
        every { providerConfigService.setConfig(userId, ApiKeyCategory.LLM, "ollama", null, null) } returns Unit

        mockMvc.perform(
            put("/users/$userId/api-keys/LLM")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider":"ollama"}""")
        ).andExpect(status().isOk)

        verify { providerConfigService.setConfig(userId, ApiKeyCategory.LLM, "ollama", null, null) }
    }

    @Test
    fun `PUT with explicit baseUrl stores it`() {
        every { userService.findById(userId) } returns user
        every { providerConfigService.hasDefaultUrl("ollama") } returns true
        every { providerConfigService.setConfig(userId, ApiKeyCategory.LLM, "ollama", null, "http://192.168.1.100:11434/v1") } returns Unit

        mockMvc.perform(
            put("/users/$userId/api-keys/LLM")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider":"ollama","baseUrl":"http://192.168.1.100:11434/v1"}""")
        ).andExpect(status().isOk)

        verify { providerConfigService.setConfig(userId, ApiKeyCategory.LLM, "ollama", null, "http://192.168.1.100:11434/v1") }
    }

    @Test
    fun `PUT with unknown provider and no baseUrl returns 400`() {
        every { userService.findById(userId) } returns user
        every { providerConfigService.hasDefaultUrl("azure") } returns false

        mockMvc.perform(
            put("/users/$userId/api-keys/LLM")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider":"azure","apiKey":"sk-123"}""")
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Base URL is required")))
    }

    @Test
    fun `PUT with unknown provider and explicit baseUrl succeeds`() {
        every { userService.findById(userId) } returns user
        every { providerConfigService.hasDefaultUrl("azure") } returns false
        every { providerConfigService.setConfig(userId, ApiKeyCategory.LLM, "azure", "sk-123", "https://my-azure.openai.azure.com") } returns Unit

        mockMvc.perform(
            put("/users/$userId/api-keys/LLM")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider":"azure","apiKey":"sk-123","baseUrl":"https://my-azure.openai.azure.com"}""")
        ).andExpect(status().isOk)
    }

    @Test
    fun `PUT with invalid category returns 400 with error message`() {
        mockMvc.perform(
            put("/users/$userId/api-keys/INVALID")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider":"openrouter","apiKey":"sk-123"}""")
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Invalid category. Must be one of: LLM, TTS, PUBLISHING, RESEARCH"))
    }

    @Test
    fun `PUT with missing provider returns 400`() {
        every { userService.findById(userId) } returns user

        mockMvc.perform(
            put("/users/$userId/api-keys/LLM")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider":" ","apiKey":"sk-123"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `PUT for non-existing user returns 404`() {
        every { userService.findById(userId) } returns null
        every { providerConfigService.hasDefaultUrl("openrouter") } returns true

        mockMvc.perform(
            put("/users/$userId/api-keys/LLM")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider":"openrouter","apiKey":"sk-123"}""")
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `GET lists configs with category, provider, and baseUrl`() {
        every { userService.findById(userId) } returns user
        every { providerConfigService.listConfigs(userId) } returns listOf(
            UserProviderConfig(userId = userId, provider = "openrouter", category = ApiKeyCategory.LLM, baseUrl = null, encryptedApiKey = "enc"),
            UserProviderConfig(userId = userId, provider = "openai", category = ApiKeyCategory.TTS, baseUrl = "https://custom.openai.com", encryptedApiKey = "enc")
        )
        every { providerConfigService.resolveDefaultUrl("openrouter") } returns "https://openrouter.ai/api"

        mockMvc.perform(get("/users/$userId/api-keys"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].category").value("LLM"))
            .andExpect(jsonPath("$[0].provider").value("openrouter"))
            .andExpect(jsonPath("$[0].baseUrl").value("https://openrouter.ai/api"))
            .andExpect(jsonPath("$[1].category").value("TTS"))
            .andExpect(jsonPath("$[1].provider").value("openai"))
            .andExpect(jsonPath("$[1].baseUrl").value("https://custom.openai.com"))
    }

    @Test
    fun `GET lists multiple providers per category`() {
        every { userService.findById(userId) } returns user
        every { providerConfigService.listConfigs(userId) } returns listOf(
            UserProviderConfig(userId = userId, provider = "openrouter", category = ApiKeyCategory.LLM, baseUrl = null, encryptedApiKey = "enc1"),
            UserProviderConfig(userId = userId, provider = "ollama", category = ApiKeyCategory.LLM, baseUrl = null, encryptedApiKey = null)
        )
        every { providerConfigService.resolveDefaultUrl("openrouter") } returns "https://openrouter.ai/api"
        every { providerConfigService.resolveDefaultUrl("ollama") } returns "http://localhost:11434"

        mockMvc.perform(get("/users/$userId/api-keys"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].category").value("LLM"))
            .andExpect(jsonPath("$[0].provider").value("openrouter"))
            .andExpect(jsonPath("$[1].category").value("LLM"))
            .andExpect(jsonPath("$[1].provider").value("ollama"))
    }

    @Test
    fun `GET returns empty array when no configs`() {
        every { userService.findById(userId) } returns user
        every { providerConfigService.listConfigs(userId) } returns emptyList()

        mockMvc.perform(get("/users/$userId/api-keys"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isEmpty)
    }

    @Test
    fun `GET for non-existing user returns 404`() {
        every { userService.findById(userId) } returns null

        mockMvc.perform(get("/users/$userId/api-keys"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE with valid category and provider returns 204`() {
        every { userService.findById(userId) } returns user
        every { providerConfigService.deleteConfig(userId, ApiKeyCategory.LLM, "openrouter") } returns true

        mockMvc.perform(delete("/users/$userId/api-keys/LLM/openrouter"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `DELETE with invalid category returns 400 with error message`() {
        mockMvc.perform(delete("/users/$userId/api-keys/INVALID/openrouter"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Invalid category. Must be one of: LLM, TTS, PUBLISHING, RESEARCH"))
    }

    @Test
    fun `DELETE non-existing config returns 404`() {
        every { userService.findById(userId) } returns user
        every { providerConfigService.deleteConfig(userId, ApiKeyCategory.TTS, "openai") } returns false

        mockMvc.perform(delete("/users/$userId/api-keys/TTS/openai"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE for non-existing user returns 404`() {
        every { userService.findById(userId) } returns null

        mockMvc.perform(delete("/users/$userId/api-keys/LLM/openrouter"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `PUT accepts lowercase category`() {
        every { userService.findById(userId) } returns user
        every { providerConfigService.hasDefaultUrl("openrouter") } returns true
        every { providerConfigService.setConfig(userId, ApiKeyCategory.LLM, "openrouter", "sk-123", null) } returns Unit

        mockMvc.perform(
            put("/users/$userId/api-keys/llm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider":"openrouter","apiKey":"sk-123"}""")
        ).andExpect(status().isOk)

        verify { providerConfigService.setConfig(userId, ApiKeyCategory.LLM, "openrouter", "sk-123", null) }
    }
}
