package com.aisummarypodcast.llm

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.CallAdvisor
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain

/**
 * Fails closed on a multi-speaker script that does not carry usable speaker tags, mirroring Spring
 * AI's `StructuredOutputValidationAdvisor` pattern (validate -> feed the error back to the model ->
 * retry) for this project's free-text, XML-tagged dialogue/interview scripts. Those scripts
 * can't use `validateSchema()` directly because it only validates typed/JSON output, and forcing
 * a ~1000-word creative script into JSON to get it would risk truncation/escaping failures.
 *
 * Two ways a script fails validation:
 *  - a tag outside [allowedRoles], such as a leaked tool-call artifact (`<function_results>`).
 *    Without this check that tag rides all the way to the TTS provider before failing with an
 *    opaque "no voice configured for role" error, the failure that took down episode 163.
 *  - no speaker tag at all. Episode 187 came back as a well-written, correctly alternating
 *    interview with every tag omitted; checking only for *wrong* tags read that as valid (the
 *    invalid-role set of an untagged script is empty), so a 5-minute compose was spent before
 *    [com.aisummarypodcast.tts.DialogueScriptParser] found zero turns and TTS threw.
 *
 * Only registered by the dialogue and interview composers, where speaker tags are mandatory;
 * briefing scripts are a single voice and legitimately carry none.
 */
class RoleTagValidationAdvisor(
    private val allowedRoles: Set<String>,
    private val maxRetries: Int = 2
) : CallAdvisor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getName(): String = "Role Tag Validation Advisor"

    override fun getOrder(): Int = 0

    override fun adviseCall(chatClientRequest: ChatClientRequest, callAdvisorChain: CallAdvisorChain): ChatClientResponse {
        var currentRequest = chatClientRequest
        var attempt = 0

        while (true) {
            val response = callAdvisorChain.copy(this).nextCall(currentRequest)
            val chatResponse = response.chatResponse()
            if (chatResponse == null || chatResponse.hasToolCalls()) return response

            val problem = validate(chatResponse.result?.output?.text.orEmpty()) ?: return response

            if (attempt >= maxRetries) {
                throw IllegalStateException(
                    "Compose LLM repeatedly ${problem.summary} after ${attempt + 1} attempts. " +
                        "Allowed roles: ${allowedRoles.joinToString { "<$it>" }}"
                )
            }

            log.warn("Compose LLM {}; retrying (attempt {}/{})", problem.summary, attempt + 1, maxRetries)
            currentRequest = currentRequest.mutate()
                .prompt(currentRequest.prompt().augmentUserMessage(problem.correction))
                .build()
            attempt++
        }
    }

    /**
     * The validation problem with [text], or null when it is a usable multi-speaker script.
     *
     * A wrong tag is reported ahead of a missing one: a response carrying only `<function_results>`
     * satisfies both checks, and naming the offending tag gives the model more to correct than
     * telling it the script is untagged.
     */
    private fun validate(text: String): TagProblem? {
        val invalidRoles = findInvalidRoles(text)
        if (invalidRoles.isNotEmpty()) {
            return TagProblem(
                summary = "produced invalid speaker tag(s) ${invalidRoles.joinToString { "<$it>" }}",
                correction = "Your response used the invalid speaker tag(s): " +
                    "${invalidRoles.joinToString { "<$it>" }}. The ONLY valid speaker tags are: " +
                    "${allowedRoles.joinToString { "<$it>" }}. Rewrite the ENTIRE script using only these tags, " +
                    "with no other tags or text outside them."
            )
        }

        if (hasSpeakerTag(text)) return null

        return TagProblem(
            summary = "produced a script with no speaker tags",
            correction = "Your response contained no speaker tags at all. Every line of spoken " +
                "text must sit inside a speaker tag, and the ONLY valid tags are: " +
                "${allowedRoles.joinToString { "<$it>…</$it>" }}. Rewrite the ENTIRE script with " +
                "each turn wrapped in one of these tags, with no text outside them."
        )
    }

    /**
     * Reports whether the script uses the speaker-tag format at all, by looking for an opener of
     * any allowed role. Deliberately accepts a square-bracketed opener (`[expert]`) and an opener
     * with no matching closer: [normalizeSquareBracketSpeakerTags] recovers the former downstream
     * and [SPEAKER_TURN_PATTERN] tolerates the latter, so treating either as "no tags" would
     * retry a script the pipeline can already use.
     */
    private fun hasSpeakerTag(text: String): Boolean =
        allowedRoles.any { text.contains("<$it>") || text.contains("[$it]") }

    private fun findInvalidRoles(text: String): Set<String> =
        SPEAKER_TURN_PATTERN.findAll(text)
            .map { it.groupValues[1] }
            .filterNot { it in allowedRoles }
            .toSet()

    private data class TagProblem(val summary: String, val correction: String)
}
