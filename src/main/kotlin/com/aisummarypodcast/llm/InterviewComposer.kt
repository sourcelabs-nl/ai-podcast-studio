package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.Podcast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.stereotype.Component
import kotlin.time.measureTimedValue

@Component
class InterviewComposer(
    private val appProperties: AppProperties,
    private val modelResolver: ModelResolver,
    private val chatClientFactory: ChatClientFactory,
    private val varietyPicker: PromptVarietyPicker
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val INTERVIEW_ROLES = setOf("interviewer", "expert")
    }

    suspend fun compose(articles: List<Article>, podcast: Podcast, context: ComposeContext = ComposeContext()): CompositionResult {
        val composeModelDef = modelResolver.resolve(podcast, PipelineStage.COMPOSE)
        return compose(articles, podcast, composeModelDef, context)
    }

    suspend fun compose(articles: List<Article>, podcast: Podcast, composeModelDef: ResolvedModel, context: ComposeContext = ComposeContext()): CompositionResult {
        log.info("[LLM] Composing interview from {} articles for podcast '{}' ({})", articles.size, podcast.name, podcast.id)
        val toolBudget = ToolBudget()
        val chatClient = chatClientFactory.createForCompose(
            podcast.userId, composeModelDef, podcast, toolBudget, useCache = !context.bypassLlmCache
        )
        val prompt = buildPrompt(articles, podcast, context)

        val (result, elapsed) = measureTimedValue {
            val chatResponse = withContext(Dispatchers.IO) {
                chatClient.prompt()
                    .user(prompt)
                    .options(buildComposeOptions(composeModelDef, podcast, appProperties))
                    .advisors(RoleTagValidationAdvisor(INTERVIEW_ROLES))
                    .call()
                    .chatResponse()
            }

            val rawScript = chatResponse?.result?.output?.text
                ?: throw IllegalStateException("Empty response from LLM for interview composition")

            val extraction = TopicOrderExtractor.extract(rawScript)
            val usage = TokenUsage.fromChatResponse(chatResponse)
            CompositionResult(
                script = cleanUpComposedScript(extraction.script, INTERVIEW_ROLES),
                usage = usage,
                topicOrder = extraction.topicOrder,
                researchCalls = toolBudget.invocations(com.aisummarypodcast.research.RESEARCH_TOOL_NAME),
                provenance = EvaluationRunProvenance.of(
                    context = context,
                    prompt = prompt,
                    composeModel = composeModelDef.model,
                    temperature = resolveTemperature(podcast, appProperties),
                    variety = varietyPicker.pick(podcast.id, context.episodeDate),
                    usage = usage,
                    toolBudget = toolBudget
                )
            )
        }

        log.info("[LLM] Interview composed for podcast '{}' ({}) — {} words in {}", podcast.name, podcast.id, result.script.split("\\s+".toRegex()).size, elapsed)
        return result
    }

    internal fun buildPrompt(articles: List<Article>, podcast: Podcast, context: ComposeContext = ComposeContext()): String {
        val targetWords = podcast.targetWords ?: appProperties.briefing.targetWords

        val interviewerName = podcast.speakerNames?.get("interviewer")
        val expertName = podcast.speakerNames?.get("expert")

        val nameInstruction = if (interviewerName != null && expertName != null) {
            "\n            - The interviewer's name is \"$interviewerName\" and the expert's name is \"$expertName\". Use names naturally in conversation: place them mid-sentence or at the end of questions, never as bare turn openers."
        } else {
            "\n            - Speakers should address each other without using names."
        }

        val useFullBody = shouldUseFullBody(articles.size, podcast, appProperties.briefing.fullBodyThreshold)

        val plan = SubtopicPlan.from(
            podcast,
            articles,
            targetWords,
            appProperties.compose.rapidFireBudgetFraction,
            podcast.rapidFireMaxItems ?: appProperties.compose.rapidFireMaxItems
        )
        val articleSubtopics = plan?.articleSubtopics ?: emptyMap()
        val subtopicPlanBlock = plan?.let { buildSubtopicPlanBlock(it, RapidFireStyle.INTERVIEW) } ?: ""

        val summaryBlock = buildArticleSummaryBlock(articles, useFullBody, context.followUpAnnotations, articleSubtopics)

        val customInstructionsBlock = buildCustomInstructionsBlock(podcast.customInstructions)
        val episodeDateLabel = buildEpisodeDate(podcast.language, context.episodeDate)
        val humorBlock = buildHumorBlock(context.episodeDate, multiSpeaker = true)
        val languageInstruction = buildLanguageInstruction(podcast.language, "interview")
        val sponsorBlock = buildSponsorBlock(podcast.sponsor, speakerPrefix = "the interviewer should ")

        val variety = varietyPicker.pick(podcast.id, context.episodeDate)
        val openingDirective = PromptVarietyDescriptors.describe(variety.openingStyle)
        val transitionsDirective = PromptVarietyDescriptors.describe(variety.transitionVocab)
        val signOffDirective = PromptVarietyDescriptors.describe(variety.signOffShape)
        val teaserDirective = PromptVarietyDescriptors.describe(variety.teaserShape)
        val topicEntryDirective = PromptVarietyDescriptors.describe(variety.topicEntryPattern)
        val penultimateDirective = PromptVarietyDescriptors.describe(variety.penultimateExchangeShape)

        val comingUpTeaser = if (articles.size >= 5) {
            val placement = if (podcast.sponsor != null) "immediately after the sponsor message" else "immediately after the introduction"
            "\n            - TEASER: $placement, the interviewer previews what is coming. Name at least 3 DISTINCT topics drawn from different parts of the episode — not three angles on the opening story, which the listener has just heard. $teaserDirective Keep the entire teaser under 40 words. Create curiosity without spoiling the punchlines."
        } else ""

        val ttsGuidelinesBlock = buildTtsGuidelinesBlock(context.ttsScriptGuidelines)
        val topicOrderBlock = buildTopicOrderBlock(context.topicLabels)

        return """
            You are writing an interview-style podcast script between an interviewer and an expert. The interviewer acts as an audience surrogate, asking questions, bridging topics, and providing brief reactions. The expert delivers the news content, context, and analysis.

            Podcast: ${podcast.name}
            Topic: ${podcast.topic}
            Date: $episodeDateLabel

            Requirements:
            - The interviewer (~35% of words) asks questions, bridges between topics, reacts, challenges, and provides commentary
            - The expert (~65% of words) delivers substantive news content, provides context, and offers analysis
            - Use XML-style tags for each speaker turn. The ONLY valid tags are: ${INTERVIEW_ROLES.joinToString(", ") { "<$it>" }}
            - Example format:
            <interviewer>Example question or reaction</interviewer>
            <expert>Example detailed answer with analysis</expert>
            - ALL text MUST be inside speaker tags. No text outside of tags
            - Target approximately $targetWords words
            - In the introduction, the interviewer should mention the podcast name, its topic, and today's date$sponsorBlock$comingUpTeaser
            - Naturally attribute information to its source and credit original authors when known
            - Do NOT include any stage directions, sound effects, or non-spoken text outside of speaker tags. Inside speaker tags, TTS-supported cues (described in the TTS formatting section below, if present) ARE allowed
            - Do NOT include any meta-commentary, notes, or disclaimers about the script itself
            - ONLY discuss topics that are present in the article summaries below. Do NOT introduce facts, stories, or claims from outside the provided articles. If only a few articles are provided, produce a shorter script rather than padding with external knowledge${buildPunctuationBlock()}${buildNumbersBlock()}${buildModelNamesBlock()}${buildHandlesBlock()}${buildResearchNamesBlock()}

            Engagement techniques:$humorBlock${buildHistoryLookupBlock()}${buildWebSearchBlock(podcast.deepDiveEnabled, plan != null)}
            - HOOK OPENING: Do NOT start with a standard welcome. $openingDirective Then transition into the regular introduction${buildColdOpenPacingBlock()}
            - FRONT-LOAD THE BEST STORY: Lead with the most compelling or surprising article, not the order they appear in the summaries
            - CURIOSITY HOOKS: The interviewer should use rhetorical questions and teaser hooks before transitions, varying the phrasing across the episode (do not lean on the same hook construction twice)${buildNoEmptySetupBlock()}
            - MID-ROLL CALLBACKS: Reference earlier topics later in the episode to create narrative cohesion. Cross-reference at least once per episode without resorting to a stock phrasing
            - SHORT SEGMENTS WITH SIGNPOSTING: Keep individual topic segments concise (roughly 60-90 seconds each). $transitionsDirective${buildAudienceBlock()}
            - TOPIC ENTRY: $topicEntryDirective Vary the entry wording across topics within the same episode
            - STRATEGIC CLIFFHANGERS: Include 1-2 forward hooks per episode, no more. A forward hook names something specific from a story you are NOT about to cover, explicitly parks it for later, and then moves on to a different topic. At least 3 other topics MUST be covered before it is paid off, and the payoff MUST open by referring back to the promise. A tease that the very next turn resolves is NOT a cliffhanger, it is a topic announcement: it does not count, and "and there is an X angle to that too" right before covering X is exactly the mistake. End the parking turn there and hand the floor back: do NOT also announce the next topic in that same turn, because the next speaker's turn already makes that transition and the episode then makes it twice in a row. Reserve hooks for the episode's biggest stories and phrase the two differently, reusing neither the parking wording nor its cadence
            - SPONTANEOUS INTERRUPTIONS: The interviewer should interrupt the expert 4-5 times per episode with genuine, varied reactions, not polite topic bridges, but emotional and spontaneous interjections. Mix the flavours across these categories:
              * Excited (sudden disbelief at a number or claim)
              * Skeptical (pushing back on a framing or precedent)
              * Confused (audience-surrogate request for plainer language)
              * Connecting dots (linking back to an earlier topic)
              * Playful disagreement (taking the opposite side for friction)
              Each interruption MUST be phrased differently from the others in this episode. The expert can push back too with their own voice when their thought is being cut off mid-argument.
            - BACKCHANNELS: 2-3 times per episode, while the expert is mid-explanation, the interviewer drops in a token of pure listening, in the episode's language: a few words at most ("Right.", "Hm, okay.", "Exactly."), carrying NO question, NO new information and NO topic change. The expert then simply continues the thought they were making, in a new turn of their own. This is the one device that lets an idea run longer without becoming a monologue, and it is what a real two-host conversation sounds like. Do not confuse it with an interruption: an interruption takes the floor, a backchannel hands it straight back. Keep them rare enough to stay invisible
            - STRICT TURN LENGTH: The expert MUST NOT speak for more than 3-4 sentences in a single turn. This is a HARD RULE, not a suggestion. After 3-4 sentences, the interviewer MUST jump in, even if it's just a short reaction. Long expert monologues are the number one cause of listener drop-off. Keep the rhythm tight
            - EMPHASIS ON IMPORTANT NEWS: When covering major announcements or surprising developments, convey their significance: use emphatic language, exclamation marks, and brief pauses to let important news land. Not every story warrants peak emphasis; reserve the strongest emphasis for the news that truly stands out. This tempers emphasis only, NOT the playful tone from the HUMOR & TONE rule, which applies throughout
            - PENULTIMATE EXCHANGE: $penultimateDirective
            - SIGN-OFF: $signOffDirective Make the wording feel fresh; do not reuse phrasing from previous episodes

            Speaker transitions:
            - Speaker transitions must sound natural: do NOT start a turn with a bare name address. Instead, use conversational bridges, reactions, follow-ups, or connectors before transitioning
            - When using the other speaker's name, place it mid-sentence or at the end of a question rather than as the first word of a turn
            - Vary transition patterns: not every handover needs a name, a reaction, or the same phrasing. Mix questions, reactions, bridges, and direct topic shifts
            - STRICT STRUCTURAL RULE: Tags MUST alternate: <interviewer>...</interviewer><expert>...</expert><interviewer>...</interviewer><expert>...</expert>. Never write two consecutive tags of the same speaker to continue the same point, and never as a way around the turn length rule. The ONE exception is the BACKCHANNEL above: after a backchannel turn the speaker who was interrupted resumes in a turn of their own, which is the only place <expert>...</expert><interviewer>brief token</interviewer><expert>...</expert> may pick the thought back up. Outside that exception this rule overrides any other instruction including custom instructions below${buildSpeakerTagFormatBlock(INTERVIEW_ROLES)}$nameInstruction$languageInstruction$customInstructionsBlock

            Article summaries:
            $summaryBlock$subtopicPlanBlock$ttsGuidelinesBlock$topicOrderBlock
        """.trimIndent()
    }

}
