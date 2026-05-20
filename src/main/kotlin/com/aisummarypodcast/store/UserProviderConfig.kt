package com.aisummarypodcast.store

enum class ApiKeyCategory {
    LLM, TTS, PUBLISHING, RESEARCH
}

data class UserProviderConfig(
    val userId: String,
    val provider: String,
    val category: ApiKeyCategory,
    val baseUrl: String? = null,
    val encryptedApiKey: String? = null
)
