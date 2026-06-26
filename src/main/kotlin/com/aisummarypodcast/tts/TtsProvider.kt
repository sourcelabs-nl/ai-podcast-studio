package com.aisummarypodcast.tts

import com.aisummarypodcast.store.PodcastStyle

data class TtsRequest(
    val script: String,
    val ttsVoices: Map<String, String>,
    val ttsSettings: Map<String, String>,
    val language: String,
    val userId: String
)

data class TtsResult(
    val audioChunks: List<ByteArray>,
    val totalCharacters: Int,
    val requiresConcatenation: Boolean,
    val model: String
)

interface TtsProvider {
    val maxChunkSize: Int
    suspend fun generate(request: TtsRequest): TtsResult
    fun scriptGuidelines(style: PodcastStyle, pronunciations: Map<String, String> = emptyMap()): String
}
