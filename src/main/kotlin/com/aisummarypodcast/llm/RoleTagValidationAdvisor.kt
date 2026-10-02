package com.aisummarypodcast.llm

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.CallAdvisor
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt

/**
 * Fails closed on a multi-speaker script that does not carry usable speaker tags, mirroring Spring
 * AI's `StructuredOutputValidationAdvisor` pattern (validate -> feed the error back to the model ->
 * retry) for this project's free-text, XML-tagged dialogue/interview scripts. Those scripts
 * can't use `validateSchema()` directly because it only validates typed/JSON output, and forcing
 * a ~1000-word creative script into JSON to get it would risk truncation/escaping failures.
 *
 * Four ways a script fails validation:
 *  - a tag outside [allowedRoles], such as a leaked tool-call artifact (`<function_results>`).
 *    Without this check that tag rides all the way to the TTS provider before failing with an
 *    opaque "no voice configured for role" error, the failure that took down episode 163.
 *  - no speaker tag at all. Episode 187 came back as a well-written, correctly alternating
 *    interview with every tag omitted; checking only for *wrong* tags read that as valid (the
 *    invalid-role set of an untagged script is empty), so a 5-minute compose was spent before
 *    [com.aisummarypodcast.tts.DialogueScriptParser] found zero turns and TTS threw.
 *  - tags that are individually valid but wrongly paired, such as `<expert>…</interviewer>`.
 *    [SPEAKER_TURN_PATTERN] matches lazily and requires the closer to name the opener's role, so a
 *    mismatched pair does not fail: the match runs on to the next matching closer and swallows
 *    every turn in between into one. Both earlier checks pass on the result, which is how episode
 *    222 shipped with the whole back half attributed to the wrong speaker.
 *  - fewer than two speakers, such as a single tagged turn in which the model talks about the task
 *    instead of performing it (episode 280).
 *
 * A retry carries the rejected response back as the model's own turn, followed by the correction,
 * so "rewrite the script" refers to something the model can see.
 *
 * Validation runs on the script as [cleanUpComposedScript] will leave it, not on the raw response,
 * so a fault the repairs in `ComposerUtils` already handle never spends a second compose call. Only
 * the invalid-role check reads the raw text, since stripping can remove a leaked tag before it is
 * seen and the leak is worth reporting.
 *
 * Only registered by the dialogue and interview composers, where speaker tags are mandatory;
 * briefing scripts are a single voice and legitimately carry none.
 */
class RoleTagValidationAdvisor(
    private val allowedRoles: Set<String>,
    private val topicOrderExtractor: TopicOrderExtractor,
    private val maxRetries: Int = 2
) : CallAdvisor {

    private val log = LoggerFactory.getLogger(javaClass)

    private val discarded = java.util.Collections.synchronizedList(mutableListOf<TokenUsage>())

    /**
     * The usage of every response this advisor rejected and re-issued. Those requests were paid
     * for, so the composer adds them to the final response's usage; otherwise the compose stage
     * would report only its last attempt. A fresh advisor is built per compose call.
     */
    val discardedUsage: List<TokenUsage> get() = discarded.toList()

    override fun getName(): String = "Role Tag Validation Advisor"

    override fun getOrder(): Int = 0

    override fun adviseCall(chatClientRequest: ChatClientRequest, callAdvisorChain: CallAdvisorChain): ChatClientResponse {
        var currentRequest = chatClientRequest
        var attempt = 0

        while (true) {
            val response = callAdvisorChain.copy(this).nextCall(currentRequest)
            val chatResponse = response.chatResponse()
            if (chatResponse == null) return response

            val responseText = chatResponse.result?.output?.text.orEmpty()
            val problem = validate(responseText) ?: return response

            if (attempt >= maxRetries) {
                throw IllegalStateException(
                    "Compose LLM repeatedly ${problem.summary} after ${attempt + 1} attempts. " +
                        "Allowed roles: ${allowedRoles.joinToString { "<$it>" }}"
                )
            }

            discarded += TokenUsage.fromChatResponse(chatResponse)
            log.warn("Compose LLM {}; retrying (attempt {}/{})", problem.summary, attempt + 1, maxRetries)
            currentRequest = currentRequest.mutate()
                .prompt(withRejectedAttempt(currentRequest.prompt(), responseText, problem.correction))
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

        if (!hasSpeakerTag(text)) {
            return TagProblem(
                summary = "produced a script with no speaker tags",
                correction = "Your response contained no speaker tags at all. Every line of spoken " +
                    "text must sit inside a speaker tag, and the ONLY valid tags are: " +
                    "${allowedRoles.joinToString { "<$it>…</$it>" }}. Rewrite the ENTIRE script with " +
                    "each turn wrapped in one of these tags, with no text outside them."
            )
        }

        // What the pipeline will actually store, so a repairable fault is not re-prompted. The
        // topic-order block is stripped first, as the composers do, or the clean-up reports it as
        // discarded untagged text on every script.
        val cleaned = cleanUpComposedScript(topicOrderExtractor.extract(text).script, allowedRoles)
        val structureProblem = findTurnStructureProblem(cleaned, allowedRoles)
            ?: return findMissingSpeakersProblem(cleaned)

        return TagProblem(
            summary = "produced a script whose speaker tags do not pair up ($structureProblem)",
            correction = "Your response has a speaker-tag structure error: $structureProblem. " +
                "Every turn must be a matching pair, opened with <role> and closed with the SAME " +
                "</role>, with no turn left unclosed and no turn opened inside another. The only " +
                "valid roles are: ${allowedRoles.joinToString { "<$it>…</$it>" }}. Rewrite the " +
                "ENTIRE script with every turn correctly opened and closed."
        )
    }

    /**
     * Rejects a script in which fewer than two speakers ever talk. Episode 280 shipped as a single
     * `<expert>` turn in which the model asked for "the original script": correctly tagged and
     * correctly paired, so every other check passed and a 10-second refusal was voiced and
     * published. A real dialogue or interview always has at least two speakers taking turns.
     */
    private fun findMissingSpeakersProblem(cleaned: String): TagProblem? {
        val speakers = SPEAKER_TURN_PATTERN.findAll(cleaned).map { it.groupValues[1] }.toSet()
        val required = minOf(MIN_SPEAKERS, allowedRoles.size)
        if (speakers.size >= required) return null

        return TagProblem(
            summary = "produced a script with only ${speakers.size} speaker(s)",
            correction = "Your response is not a usable episode script: only ${speakers.size} speaker(s) " +
                "talk in it. Write the COMPLETE episode script from the source material in the first " +
                "message, as a conversation in which the speakers ${allowedRoles.joinToString { "<$it>" }} " +
                "take turns, with every turn wrapped in its speaker tag."
        )
    }

    /**
     * The retry prompt: the original conversation, the rejected response as the model's own turn,
     * then the correction. The rejected response must be in the conversation, or a correction that
     * says "rewrite the script" refers to a script the model cannot see; episode 280's model
     * answered exactly that ("I don't have the original script") and the answer was voiced.
     */
    private fun withRejectedAttempt(prompt: Prompt, rejected: String, correction: String): Prompt =
        Prompt(prompt.instructions + AssistantMessage(rejected) + UserMessage(correction), prompt.options)

    /**
     * Reports whether the script uses the speaker-tag format at all, by looking for an opener of
     * any allowed role. Deliberately accepts a square-bracketed opener (`[expert]`) and an opener
     * with no matching closer: [normalizeSquareBracketSpeakerTags] recovers the former downstream
     * and [closeUnterminatedFinalTurn] the latter, so treating either as "no tags" would retry a
     * script the pipeline can already use.
     */
    private fun hasSpeakerTag(text: String): Boolean =
        allowedRoles.any { text.contains("<$it>") || text.contains("[$it]") }

    private fun findInvalidRoles(text: String): Set<String> =
        SPEAKER_TURN_PATTERN.findAll(text)
            .map { it.groupValues[1] }
            .filterNot { it in allowedRoles }
            .toSet()

    private data class TagProblem(val summary: String, val correction: String)

    private companion object {
        const val MIN_SPEAKERS = 2
    }
}
