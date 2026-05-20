package com.aisummarypodcast.user

import com.aisummarypodcast.config.ApiKeyEncryptor
import com.aisummarypodcast.store.ApiKeyCategory
import com.aisummarypodcast.store.UserProviderConfig
import com.aisummarypodcast.store.UserProviderConfigRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class UserProviderConfigServiceTest {

    private val repository = mockk<UserProviderConfigRepository>()
    private val encryptor = mockk<ApiKeyEncryptor>()
    private val service = UserProviderConfigService(repository, encryptor)

    private val userId = "user-1"

    @Test
    fun `resolveConfig returns decrypted key and stored base URL when present`() {
        val config = UserProviderConfig(userId, "openrouter", ApiKeyCategory.LLM, "https://custom.api/v1", "encrypted-value")
        every { repository.findByUserIdAndCategory(userId, ApiKeyCategory.LLM) } returns listOf(config)
        every { encryptor.decrypt("encrypted-value") } returns "sk-real-key"

        val result = service.resolveConfig(userId, ApiKeyCategory.LLM)

        assertEquals("sk-real-key", result?.apiKey)
        assertEquals("https://custom.api/v1", result?.baseUrl)
    }

    @Test
    fun `resolveConfig uses provider default URL when base URL is null`() {
        val config = UserProviderConfig(userId, "openrouter", ApiKeyCategory.LLM, null, "encrypted-value")
        every { repository.findByUserIdAndCategory(userId, ApiKeyCategory.LLM) } returns listOf(config)
        every { encryptor.decrypt("encrypted-value") } returns "sk-real-key"

        val result = service.resolveConfig(userId, ApiKeyCategory.LLM)

        assertEquals("sk-real-key", result?.apiKey)
        assertEquals("https://openrouter.ai/api", result?.baseUrl)
    }

    @Test
    fun `resolveConfig returns null apiKey for Ollama provider with no key`() {
        val config = UserProviderConfig(userId, "ollama", ApiKeyCategory.LLM, null, null)
        every { repository.findByUserIdAndCategory(userId, ApiKeyCategory.LLM) } returns listOf(config)

        val result = service.resolveConfig(userId, ApiKeyCategory.LLM)

        assertNull(result?.apiKey)
        assertEquals("http://localhost:11434/v1", result?.baseUrl)
    }

    @Test
    fun `resolveConfig picks first config when multiple exist for category`() {
        val config1 = UserProviderConfig(userId, "openrouter", ApiKeyCategory.LLM, null, "enc-or")
        val config2 = UserProviderConfig(userId, "ollama", ApiKeyCategory.LLM, null, null)
        every { repository.findByUserIdAndCategory(userId, ApiKeyCategory.LLM) } returns listOf(config1, config2)
        every { encryptor.decrypt("enc-or") } returns "sk-or-key"

        val result = service.resolveConfig(userId, ApiKeyCategory.LLM)

        assertEquals("sk-or-key", result?.apiKey)
        assertEquals("https://openrouter.ai/api", result?.baseUrl)
    }

    @Test
    fun `resolveConfig falls back to env var when no user config exists`() {
        every { repository.findByUserIdAndCategory(userId, ApiKeyCategory.LLM) } returns emptyList()

        service.resolveConfig(userId, ApiKeyCategory.LLM)

        verify { repository.findByUserIdAndCategory(userId, ApiKeyCategory.LLM) }
    }

    @Test
    fun `setConfig encrypts key and saves with category and base URL`() {
        every { encryptor.encrypt("sk-123") } returns "encrypted-123"
        every { repository.save(any()) } returns Unit

        service.setConfig(userId, ApiKeyCategory.TTS, "openai", "sk-123", "https://custom.openai.com")

        verify {
            repository.save(
                UserProviderConfig(
                    userId = userId,
                    provider = "openai",
                    category = ApiKeyCategory.TTS,
                    baseUrl = "https://custom.openai.com",
                    encryptedApiKey = "encrypted-123"
                )
            )
        }
    }

    @Test
    fun `setConfig saves null encrypted key when apiKey is null and no existing config`() {
        every { repository.save(any()) } returns Unit
        every { repository.findByUserIdAndCategoryAndProvider(userId, ApiKeyCategory.LLM, "ollama") } returns null

        service.setConfig(userId, ApiKeyCategory.LLM, "ollama", null, null)

        verify {
            repository.save(
                UserProviderConfig(
                    userId = userId,
                    provider = "ollama",
                    category = ApiKeyCategory.LLM,
                    baseUrl = null,
                    encryptedApiKey = null
                )
            )
        }
    }

    @Test
    fun `deleteConfig delegates to repository by category and provider`() {
        every { repository.deleteByUserIdAndCategoryAndProvider(userId, ApiKeyCategory.LLM, "openrouter") } returns 1

        val result = service.deleteConfig(userId, ApiKeyCategory.LLM, "openrouter")

        assertEquals(true, result)
    }

    @Test
    fun `deleteConfig returns false when nothing deleted`() {
        every { repository.deleteByUserIdAndCategoryAndProvider(userId, ApiKeyCategory.TTS, "openai") } returns 0

        val result = service.deleteConfig(userId, ApiKeyCategory.TTS, "openai")

        assertEquals(false, result)
    }

    @Test
    fun `listConfigs returns all configs for user`() {
        val configs = listOf(
            UserProviderConfig(userId, "openrouter", ApiKeyCategory.LLM, null, "enc1"),
            UserProviderConfig(userId, "openai", ApiKeyCategory.TTS, null, "enc2")
        )
        every { repository.findByUserId(userId) } returns configs

        val result = service.listConfigs(userId)

        assertEquals(2, result.size)
        assertEquals(ApiKeyCategory.LLM, result[0].category)
        assertEquals(ApiKeyCategory.TTS, result[1].category)
    }

    @Test
    fun `resolveConfig with provider returns matching config`() {
        val config = UserProviderConfig(userId, "ollama", ApiKeyCategory.LLM, null, null)
        every { repository.findByUserIdAndCategoryAndProvider(userId, ApiKeyCategory.LLM, "ollama") } returns config

        val result = service.resolveConfig(userId, ApiKeyCategory.LLM, "ollama")

        assertNull(result?.apiKey)
        assertEquals("http://localhost:11434/v1", result?.baseUrl)
    }

    @Test
    fun `resolveConfig with provider falls back to env var for openrouter`() {
        every { repository.findByUserIdAndCategoryAndProvider(userId, ApiKeyCategory.LLM, "openrouter") } returns null

        // This test just verifies the method doesn't throw and delegates to global fallback
        service.resolveConfig(userId, ApiKeyCategory.LLM, "openrouter")

        verify { repository.findByUserIdAndCategoryAndProvider(userId, ApiKeyCategory.LLM, "openrouter") }
    }

    @Test
    fun `resolveConfig with provider returns null for non-fallback provider without config`() {
        every { repository.findByUserIdAndCategoryAndProvider(userId, ApiKeyCategory.LLM, "ollama") } returns null

        val result = service.resolveConfig(userId, ApiKeyCategory.LLM, "ollama")

        assertNull(result)
    }

    @Test
    fun `hasDefaultUrl returns true for known providers`() {
        assertEquals(true, service.hasDefaultUrl("openrouter"))
        assertEquals(true, service.hasDefaultUrl("openai"))
        assertEquals(true, service.hasDefaultUrl("ollama"))
    }

    @Test
    fun `hasDefaultUrl returns false for unknown providers`() {
        assertEquals(false, service.hasDefaultUrl("azure"))
        assertEquals(false, service.hasDefaultUrl("custom"))
    }

    @Test
    fun `setConfig encrypts Tavily research key`() {
        every { encryptor.encrypt("tvly-secret") } returns "encrypted-tvly"
        every { repository.save(any()) } returns Unit

        service.setConfig(userId, ApiKeyCategory.RESEARCH, "tavily", "tvly-secret", null)

        verify {
            repository.save(
                UserProviderConfig(
                    userId = userId,
                    provider = "tavily",
                    category = ApiKeyCategory.RESEARCH,
                    baseUrl = null,
                    encryptedApiKey = "encrypted-tvly"
                )
            )
        }
    }

    @Test
    fun `resolveConfig with tavily returns user key when configured`() {
        val config = UserProviderConfig(userId, "tavily", ApiKeyCategory.RESEARCH, null, "encrypted-tvly")
        every {
            repository.findByUserIdAndCategoryAndProvider(userId, ApiKeyCategory.RESEARCH, "tavily")
        } returns config
        every { encryptor.decrypt("encrypted-tvly") } returns "tvly-user-key"

        val result = service.resolveConfig(userId, ApiKeyCategory.RESEARCH, "tavily")

        assertEquals("tvly-user-key", result?.apiKey)
        assertEquals("https://api.tavily.com", result?.baseUrl)
    }

    @Test
    fun `hasDefaultUrl recognises tavily`() {
        assertEquals(true, service.hasDefaultUrl("tavily"))
    }

    @Test
    fun `hasDefaultUrl is case-insensitive`() {
        assertEquals(true, service.hasDefaultUrl("OpenRouter"))
        assertEquals(true, service.hasDefaultUrl("OLLAMA"))
    }
}
