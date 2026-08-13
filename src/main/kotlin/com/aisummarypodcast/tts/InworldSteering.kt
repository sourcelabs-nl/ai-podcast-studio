package com.aisummarypodcast.tts

/**
 * Inworld steering instructions (`[warm and conversational with an easy pace]`) stay in force until
 * they are changed or cleared with `[reset]`, but only within a single synthesis request. Chunking
 * splits a speaker turn into several independent requests, so without help the direction silently
 * expires at the first splice. This re-emits the active instruction at the head of every chunk that
 * does not already open with one.
 */
object InworldSteering {

    /** `inworld-tts-2` is the only model that understands steering; `-flash` variants do not. */
    private const val STEERING_MODEL = "inworld-tts-2"

    private const val RESET_TAG = "reset"

    fun supportsSteering(modelId: String): Boolean = modelId == STEERING_MODEL

    /**
     * @param chunks the chunks of a single monologue script or a single dialogue turn, in order.
     *   Tracking is per call, so a direction given to one speaker never leaks into another's voice.
     */
    fun reemitInstructions(chunks: List<String>): List<String> {
        var active: String? = null
        return chunks.map { chunk ->
            val prefixed = if (active != null && !opensWithInstruction(chunk)) "[$active] $chunk" else chunk
            active = activeAfter(chunk, active)
            prefixed
        }
    }

    private fun opensWithInstruction(chunk: String): Boolean {
        val match = InworldScriptPostProcessor.BRACKETED_TAG.find(chunk.trimStart()) ?: return false
        return match.range.first == 0 && InworldScriptPostProcessor.isSteeringInstruction(match.groupValues[1])
    }

    private fun activeAfter(chunk: String, current: String?): String? {
        var active = current
        for (match in InworldScriptPostProcessor.BRACKETED_TAG.findAll(chunk)) {
            val tag = match.groupValues[1].trim()
            when {
                tag.equals(RESET_TAG, ignoreCase = true) -> active = null
                InworldScriptPostProcessor.isSteeringInstruction(tag) -> active = tag
            }
        }
        return active
    }
}
