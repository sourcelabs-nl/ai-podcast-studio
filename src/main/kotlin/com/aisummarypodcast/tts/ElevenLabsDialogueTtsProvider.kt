package com.aisummarypodcast.tts

import com.aisummarypodcast.store.PodcastStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ElevenLabsDialogueTtsProvider(
    private val apiClient: ElevenLabsApiClient
) : TtsProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    override val maxChunkSize: Int = 5000

    override fun scriptGuidelines(style: PodcastStyle, pronunciations: Map<String, String>): String =
        "You MAY include emotion cues in square brackets to guide vocal delivery (e.g., [cheerfully], [seriously], [with excitement]). Keep cues natural and sparse."

    companion object {
        const val MAX_CHARS = 5000
    }

    override suspend fun generate(request: TtsRequest): TtsResult {
        val turns = DialogueScriptParser.parse(request.script)
        if (turns.isEmpty()) {
            throw IllegalStateException("Dialogue script produced no speaker turns")
        }

        val inputs = turns.map { turn ->
            val voiceId = request.ttsVoices[turn.role]
                ?: throw IllegalStateException(
                    "No voice configured for role '${turn.role}'. Available roles: ${request.ttsVoices.keys.joinToString()}"
                )
            DialogueInput(text = turn.text, voice_id = voiceId)
        }

        val uniqueVoices = inputs.map { it.voice_id }.distinct()
        val batches = batchInputs(inputs)
        log.info("Generating ElevenLabs dialogue: {} turns, {} unique voices, {} batch(es)", inputs.size, uniqueVoices.size, batches.size)

        val settings = request.ttsSettings.ifEmpty { null }
        val audioChunks = withContext(Dispatchers.IO) {
            batches.mapIndexed { index, batch ->
                log.info("Generating dialogue batch {}/{} ({} turns, {} chars)", index + 1, batches.size, batch.size, batch.sumOf { it.text.length })
                apiClient.textToDialogue(request.userId, batch, settings)
                    .also { request.progress?.onChunkCompleted(index + 1, batches.size) }
            }
        }
        val totalCharacters = turns.sumOf { it.text.length }

        return TtsResult(
            audioChunks = audioChunks,
            totalCharacters = totalCharacters,
            requiresConcatenation = batches.size > 1,
            model = "eleven_v3"
        )
    }

    internal fun batchInputs(inputs: List<DialogueInput>): List<List<DialogueInput>> {
        val batches = mutableListOf<MutableList<DialogueInput>>()
        var currentBatch = mutableListOf<DialogueInput>()
        var currentChars = 0

        for (input in inputs) {
            if (currentBatch.isNotEmpty() && currentChars + input.text.length > MAX_CHARS) {
                batches.add(currentBatch)
                currentBatch = mutableListOf()
                currentChars = 0
            }
            currentBatch.add(input)
            currentChars += input.text.length
        }

        if (currentBatch.isNotEmpty()) {
            batches.add(currentBatch)
        }

        return batches
    }
}
