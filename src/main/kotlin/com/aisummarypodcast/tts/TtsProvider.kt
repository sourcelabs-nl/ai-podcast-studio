package com.aisummarypodcast.tts

import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastStyle

/**
 * Reports synthesis progress as individual chunks finish. Synthesis is the slow part of producing
 * audio and a completed chunk is its only observable milestone, so a caller that streams to a
 * client (the script preview audio endpoint) can turn these into live progress events.
 */
fun interface TtsProgressListener {
    fun onChunkCompleted(completed: Int, total: Int)
}

data class TtsRequest(
    val script: String,
    val ttsVoices: Map<String, String>,
    val ttsSettings: Map<String, String>,
    val language: String,
    val userId: String,
    val progress: TtsProgressListener? = null
) {
    companion object {
        /**
         * The request a podcast's script is synthesized with, whether it becomes an episode or a
         * preview. Both paths must apply the same sanitization and the same voice/settings
         * fallbacks, or a preview would not sound like the episode it is previewing.
         */
        fun forPodcast(podcast: Podcast, script: String, progress: TtsProgressListener? = null) = TtsRequest(
            script = TtsScriptSanitizer.sanitize(script),
            ttsVoices = podcast.ttsVoices ?: mapOf("default" to "nova"),
            ttsSettings = podcast.ttsSettings ?: emptyMap(),
            language = podcast.language,
            userId = podcast.userId,
            progress = progress
        )
    }
}

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
