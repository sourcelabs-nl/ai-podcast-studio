package com.aisummarypodcast.eval

import com.aisummarypodcast.llm.ChatClientFactory
import com.aisummarypodcast.llm.CostEstimator
import com.aisummarypodcast.llm.OpenRouterRouting
import com.aisummarypodcast.llm.ResolvedModel
import com.aisummarypodcast.llm.TokenUsage
import com.aisummarypodcast.llm.withRoutingAndReasoning
import com.aisummarypodcast.tts.DialogueScriptParser
import io.github.resilience4j.kotlin.retry.executeSuspendFunction
import io.github.resilience4j.retry.RetryRegistry
import org.slf4j.LoggerFactory
import org.springframework.ai.converter.BeanOutputConverter
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import kotlin.math.roundToInt

/**
 * What one judge call produced: the anchors, and what it cost to get them.
 */
data class ScriptJudgement(
    val anchors: ScriptJudgeAnchors,
    val usage: TokenUsage,
    val costCents: Int?
)

/**
 * Asks a model where the attention devices are in a script.
 *
 * The judge is only ever asked for positions. It is not asked how far apart two turns are, how
 * evenly humor is spread, or how good the script is: a model asked to count over a long document
 * gets the count wrong invisibly, while a model asked to point at a turn can be checked by opening
 * the script at that turn. Everything numeric is computed from the returned indices by
 * [AttentionScoring].
 */
@Component
class ScriptJudge(
    private val chatClientFactory: ChatClientFactory,
    private val jsonMapper: JsonMapper,
    private val retryRegistry: RetryRegistry
) {

    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun judge(script: String, userId: String, evalModel: ResolvedModel): ScriptJudgement {
        val numbered = numberTurns(script)
        require(numbered.isNotEmpty()) { "Cannot judge a script with no speaker turns" }

        val chatClient = chatClientFactory.createForModel(userId, evalModel)
        val prompt = buildPrompt(numbered)
        var attempt = 0

        return retryRegistry.retry("script-judge").executeSuspendFunction {
            attempt++
            val converter = BeanOutputConverter(ScriptJudgeAnchors::class.java, jsonMapper)
            val responseEntity = chatClient.prompt()
                .user(promptForAttempt(prompt, attempt))
                .options(
                    OpenAiChatOptions.builder()
                        .model(evalModel.model)
                        .temperature(0.3)
                        // Locating turns is recall, not deliberation, and reasoning tokens bill as
                        // output on every one of the archive's scripts.
                        .withRoutingAndReasoning(evalModel.provider, OpenRouterRouting.NO_REASONING)
                )
                .call()
                .responseEntity(converter)

            val anchors = responseEntity.entity()
                ?: throw IllegalStateException("Judge returned no parseable anchors for a ${numbered.size}-turn script")
            val usage = TokenUsage.fromChatResponse(responseEntity.response())
            val costCents = CostEstimator.resolveLlmCost(usage, evalModel.cost).costCents?.roundToInt()

            val clean = withinRange(anchors, numbered.size)
            log.info(
                "[EVAL] Judged a {}-turn script: {} promises, {} humor beats, {} teaser topics",
                numbered.size, clean.promises.size, clean.humorBeats.size, clean.teaserTopics.size
            )
            ScriptJudgement(clean, usage, costCents)
        }
    }

    /**
     * Drops anchors pointing outside the script.
     *
     * An index the script does not have cannot be checked by opening the script there, which is the
     * one property the anchors exist for. Keeping it would put a turn number into a stored score
     * that refers to nothing.
     */
    internal fun withinRange(anchors: ScriptJudgeAnchors, turnCount: Int): ScriptJudgeAnchors {
        fun valid(turn: Int) = turn in 0 until turnCount
        val promises = anchors.promises
            .filter { valid(it.promiseTurn) }
            .map { if (it.payoffTurn != null && !valid(it.payoffTurn)) it.copy(payoffTurn = null) else it }
        val dropped = (anchors.promises.size - promises.size) +
            anchors.humorBeats.count { !valid(it.turn) }
        if (dropped > 0) {
            log.warn("[EVAL] Dropped {} anchor(s) pointing outside a {}-turn script", dropped, turnCount)
        }
        return anchors.copy(promises = promises, humorBeats = anchors.humorBeats.filter { valid(it.turn) })
    }

    private fun numberTurns(script: String): List<String> =
        DialogueScriptParser.parse(script).mapIndexed { index, turn -> "[$index] ${turn.role}: ${turn.text}" }

    internal fun buildPrompt(numberedTurns: List<String>): String = """
        You are reading a podcast script as a structural analyst. Each turn is prefixed with its
        index in square brackets. Report where three things happen. Report positions only: do not
        count anything, do not measure distances, and do not rate the script.

        1. Forward-looking promises. A turn that announces something it does not then deliver:
           "we'll come back to that", "the strange part comes later", a question parked rather than
           answered. For each one give the turn index that makes the promise, and the turn index
           that finally delivers it. If nothing in the script delivers it, give null.
        2. Humor beats. A turn containing a joke, a piece of wordplay, or a deliberately funny
           observation. Give the turn index, the speaker role exactly as it appears on that turn,
           and whether the beat plays off the turn immediately before it.
        3. Teaser topics. The distinct subjects named in the script's opening as coming up later.
           Give them as short phrases, in the script's own language.

        Report only what is on the page. An absent device is a finding: return an empty list rather
        than the nearest thing you can find.

        Script:
        ${numberedTurns.joinToString("\n")}

        Respond with a JSON object containing:
        - "promises": array of { "promiseTurn": integer, "payoffTurn": integer or null }
        - "humorBeats": array of { "turn": integer, "role": string, "reactsToPrevious": boolean }
        - "teaserTopics": array of strings
    """.trimIndent()

    /**
     * Returns the prompt for [attempt], appending a correction from the second attempt on.
     *
     * A retry must never send the byte-identical prompt: `CachingChatModel` keys on prompt text, so
     * an unparseable answer is cached and every identical retry would replay it in milliseconds
     * without reaching the model.
     */
    internal fun promptForAttempt(prompt: String, attempt: Int): String =
        if (attempt <= 1) prompt else
            "$prompt\n\nRetry $attempt: your previous response could not be parsed as JSON. Respond " +
                "with the raw JSON object only. Do not include reasoning, commentary, or markdown " +
                "code fences, and do not write anything before or after the JSON."
}
