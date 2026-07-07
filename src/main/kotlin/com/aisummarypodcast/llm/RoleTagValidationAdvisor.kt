package com.aisummarypodcast.llm

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.CallAdvisor
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain

/**
 * Fails closed on speaker tags outside [allowedRoles], mirroring Spring AI's
 * `StructuredOutputValidationAdvisor` pattern (validate -> feed the error back to the model ->
 * retry) for this project's free-text, XML-tagged dialogue/interview scripts. Those scripts
 * can't use `validateSchema()` directly because it only validates typed/JSON output, and forcing
 * a ~1000-word creative script into JSON to get it would risk truncation/escaping failures.
 *
 * Without this, a leaked tool-call artifact (e.g. `<function_results>`) rides all the way to the
 * TTS provider before failing with an opaque "no voice configured for role" error — the exact
 * failure that took down episode 163.
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

            val invalidRoles = findInvalidRoles(chatResponse.result?.output?.text.orEmpty())
            if (invalidRoles.isEmpty()) return response

            if (attempt >= maxRetries) {
                throw IllegalStateException(
                    "Compose LLM repeatedly produced invalid speaker tag(s) ${invalidRoles.joinToString { "<$it>" }} " +
                        "after ${attempt + 1} attempts. Allowed roles: ${allowedRoles.joinToString { "<$it>" }}"
                )
            }

            log.warn(
                "Compose LLM produced invalid speaker tag(s) {}; retrying (attempt {}/{})",
                invalidRoles, attempt + 1, maxRetries
            )
            val errorMessage = "Your response used the invalid speaker tag(s): " +
                "${invalidRoles.joinToString { "<$it>" }}. The ONLY valid speaker tags are: " +
                "${allowedRoles.joinToString { "<$it>" }}. Rewrite the ENTIRE script using only these tags, " +
                "with no other tags or text outside them."
            currentRequest = currentRequest.mutate()
                .prompt(currentRequest.prompt().augmentUserMessage(errorMessage))
                .build()
            attempt++
        }
    }

    private fun findInvalidRoles(text: String): Set<String> =
        SPEAKER_TURN_PATTERN.findAll(text)
            .map { it.groupValues[1] }
            .filterNot { it in allowedRoles }
            .toSet()
}
