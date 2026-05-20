package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.Podcast
import org.slf4j.LoggerFactory
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.stereotype.Component
import java.time.LocalDate
import kotlin.time.measureTimedValue

@Component
class InterviewComposer(
    private val appProperties: AppProperties,
    private val modelResolver: ModelResolver,
    private val chatClientFactory: ChatClientFactory,
    private val varietyPicker: PromptVarietyPicker
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun compose(articles: List<Article>, podcast: Podcast, ttsScriptGuidelines: String = "", followUpAnnotations: Map<Long, String> = emptyMap(), topicLabels: List<String> = emptyList()): CompositionResult {
        val composeModelDef = modelResolver.resolve(podcast, PipelineStage.COMPOSE)
        return compose(articles, podcast, composeModelDef, ttsScriptGuidelines, followUpAnnotations, topicLabels)
    }

    fun compose(articles: List<Article>, podcast: Podcast, composeModelDef: ResolvedModel, ttsScriptGuidelines: String = "", followUpAnnotations: Map<Long, String> = emptyMap(), topicLabels: List<String> = emptyList()): CompositionResult {
        log.info("[LLM] Composing interview from {} articles for podcast '{}' ({})", articles.size, podcast.name, podcast.id)
        val toolBudget = ToolBudget()
        val chatClient = chatClientFactory.createForCompose(podcast.userId, composeModelDef, podcast, toolBudget)
        val prompt = buildPrompt(articles, podcast, ttsScriptGuidelines, followUpAnnotations, topicLabels)
        val temperature = resolveTemperature(podcast, appProperties)

        val (result, elapsed) = measureTimedValue {
            val chatResponse = chatClient.prompt()
                .user(prompt)
                .options(OpenAiChatOptions.builder().model(composeModelDef.model).temperature(temperature).build())
                .call()
                .chatResponse()

            val rawScript = chatResponse?.result?.output?.text
                ?: throw IllegalStateException("Empty response from LLM for interview composition")

            val extraction = TopicOrderExtractor.extract(rawScript)
            val usage = TokenUsage.fromChatResponse(chatResponse)
            CompositionResult(
                script = extraction.script,
                usage = usage,
                topicOrder = extraction.topicOrder,
                researchCalls = toolBudget.invocations(com.aisummarypodcast.research.RESEARCH_TOOL_NAME)
            )
        }

        log.info("[LLM] Interview composed for podcast '{}' ({}) — {} words in {}", podcast.name, podcast.id, result.script.split("\\s+".toRegex()).size, elapsed)
        return result
    }

    internal fun buildPrompt(articles: List<Article>, podcast: Podcast, ttsScriptGuidelines: String = "", followUpAnnotations: Map<Long, String> = emptyMap(), topicLabels: List<String> = emptyList()): String {
        val targetWords = podcast.targetWords ?: appProperties.briefing.targetWords

        val interviewerName = podcast.speakerNames?.get("interviewer")
        val expertName = podcast.speakerNames?.get("expert")

        val nameInstruction = if (interviewerName != null && expertName != null) {
            "\n            - The interviewer's name is \"$interviewerName\" and the expert's name is \"$expertName\". Use names naturally in conversation: place them mid-sentence or at the end of questions, never as bare turn openers."
        } else {
            "\n            - Speakers should address each other without using names."
        }

        val useFullBody = shouldUseFullBody(articles.size, podcast, appProperties.briefing.fullBodyThreshold)

        val plan = SubtopicPlan.from(podcast, articles, targetWords, appProperties.compose.rapidFireBudgetFraction)
        val articleSubtopics = plan?.articleSubtopics ?: emptyMap()
        val subtopicPlanBlock = plan?.let { buildSubtopicPlanBlock(it, RapidFireStyle.INTERVIEW) } ?: ""

        val summaryBlock = buildArticleSummaryBlock(articles, useFullBody, followUpAnnotations, articleSubtopics)

        val customInstructionsBlock = buildCustomInstructionsBlock(podcast.customInstructions)
        val currentDate = buildCurrentDate(podcast.language)
        val toneBlock = buildToneBlock()
        val languageInstruction = buildLanguageInstruction(podcast.language, "interview")
        val sponsorBlock = buildSponsorBlock(podcast.sponsor, speakerPrefix = "the interviewer should ")

        val variety = varietyPicker.pick(podcast.id, LocalDate.now())
        val openingDirective = PromptVarietyDescriptors.describe(variety.openingStyle)
        val transitionsDirective = PromptVarietyDescriptors.describe(variety.transitionVocab)
        val signOffDirective = PromptVarietyDescriptors.describe(variety.signOffShape)
        val teaserDirective = PromptVarietyDescriptors.describe(variety.teaserShape)
        val topicEntryDirective = PromptVarietyDescriptors.describe(variety.topicEntryPattern)
        val penultimateDirective = PromptVarietyDescriptors.describe(variety.penultimateExchangeShape)

        val comingUpTeaser = if (articles.size >= 5) {
            val placement = if (podcast.sponsor != null) "immediately after the sponsor message" else "immediately after the introduction"
            "\n            - TEASER: $placement, the interviewer previews the most interesting topics. $teaserDirective Keep the entire teaser under 25 words. Create curiosity without spoiling the punchlines."
        } else ""

        val ttsGuidelinesBlock = buildTtsGuidelinesBlock(ttsScriptGuidelines)
        val topicOrderBlock = buildTopicOrderBlock(topicLabels)

        return """
            You are writing an interview-style podcast script between an interviewer and an expert. The interviewer acts as an audience surrogate, asking questions, bridging topics, and providing brief reactions. The expert delivers the news content, context, and analysis.

            Podcast: ${podcast.name}
            Topic: ${podcast.topic}
            Date: $currentDate

            Requirements:
            - The interviewer (~35% of words) asks questions, bridges between topics, reacts, challenges, and provides commentary
            - The expert (~65% of words) delivers substantive news content, provides context, and offers analysis
            - Use XML-style tags for each speaker turn. The ONLY valid tags are: <interviewer>, <expert>
            - Example format:
            <interviewer>Example question or reaction</interviewer>
            <expert>Example detailed answer with analysis</expert>
            - ALL text MUST be inside speaker tags. No text outside of tags
            - Target approximately $targetWords words
            - In the introduction, the interviewer should mention the podcast name, its topic, and today's date$sponsorBlock$comingUpTeaser
            - Naturally attribute information to its source and credit original authors when known
            - Do NOT include any stage directions, sound effects, or non-spoken text outside of speaker tags. Inside speaker tags, TTS-supported cues (described in the TTS formatting section below, if present) ARE allowed
            - Do NOT include any meta-commentary, notes, or disclaimers about the script itself
            - ONLY discuss topics that are present in the article summaries below. Do NOT introduce facts, stories, or claims from outside the provided articles. If only a few articles are provided, produce a shorter script rather than padding with external knowledge${buildPunctuationBlock()}

            Engagement techniques:${buildHistoryLookupBlock()}${buildWebSearchBlock(podcast.deepDiveEnabled, plan != null)}
            - HOOK OPENING: Do NOT start with a standard welcome. $openingDirective Then transition into the regular introduction
            - FRONT-LOAD THE BEST STORY: Lead with the most compelling or surprising article, not the order they appear in the summaries
            - CURIOSITY HOOKS: The interviewer should use rhetorical questions and teaser hooks before transitions, varying the phrasing across the episode (do not lean on the same hook construction twice)
            - MID-ROLL CALLBACKS: Reference earlier topics later in the episode to create narrative cohesion. Cross-reference at least once per episode without resorting to a stock phrasing
            - SHORT SEGMENTS WITH SIGNPOSTING: Keep individual topic segments concise (roughly 60-90 seconds each). $transitionsDirective
            - TOPIC ENTRY: $topicEntryDirective Vary the entry wording across topics within the same episode
            - STRATEGIC CLIFFHANGERS: Include 2-3 forward hooks spread across the episode, teasing something from a later story before transitioning. Phrase each cliffhanger differently (no two should share the same construction). Do NOT overuse, only 2-3 per episode at natural transition points
            - SPONTANEOUS INTERRUPTIONS: The interviewer should interrupt the expert 4-5 times per episode with genuine, varied reactions, not polite topic bridges, but emotional and spontaneous interjections. Mix the flavours across these categories:
              * Excited (sudden disbelief at a number or claim)
              * Skeptical (pushing back on a framing or precedent)
              * Confused (audience-surrogate request for plainer language)
              * Connecting dots (linking back to an earlier topic)
              * Playful disagreement (taking the opposite side for friction)
              Each interruption MUST be phrased differently from the others in this episode. The expert can push back too with their own voice when their thought is being cut off mid-argument.
            - STRICT TURN LENGTH: The expert MUST NOT speak for more than 3-4 sentences in a single turn. This is a HARD RULE, not a suggestion. After 3-4 sentences, the interviewer MUST jump in, even if it's just a short reaction. Long expert monologues are the number one cause of listener drop-off. Keep the rhythm tight
            - EMPHASIS ON IMPORTANT NEWS: When covering major announcements or surprising developments, convey their significance: use emphatic language, exclamation marks, and brief pauses to let important news land. Not everything is exciting; save the energy for what truly stands out
            - PENULTIMATE EXCHANGE: $penultimateDirective
            - SIGN-OFF: $signOffDirective Make the wording feel fresh; do not reuse phrasing from previous episodes$toneBlock

            Speaker transitions:
            - Speaker transitions must sound natural: do NOT start a turn with a bare name address. Instead, use conversational bridges, reactions, follow-ups, or connectors before transitioning
            - When using the other speaker's name, place it mid-sentence or at the end of a question rather than as the first word of a turn
            - Vary transition patterns: not every handover needs a name, a reaction, or the same phrasing. Mix questions, reactions, bridges, and direct topic shifts
            - STRICT STRUCTURAL RULE: NEVER place two consecutive tags of the same speaker. The pattern <expert>...</expert><expert>...</expert> or <interviewer>...</interviewer><interviewer>...</interviewer> is ABSOLUTELY FORBIDDEN and will break the TTS pipeline. Tags MUST strictly alternate: <interviewer>...</interviewer><expert>...</expert><interviewer>...</interviewer><expert>...</expert>. This rule overrides any other instruction including custom instructions below$nameInstruction$languageInstruction$customInstructionsBlock

            Article summaries:
            $summaryBlock$subtopicPlanBlock$ttsGuidelinesBlock$topicOrderBlock
        """.trimIndent()
    }

}
