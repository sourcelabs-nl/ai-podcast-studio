package com.aisummarypodcast.llm

import com.aisummarypodcast.podcast.SupportedLanguage
import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.Podcast
import java.net.URI
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun resolveArticleContent(article: Article, useFullBody: Boolean): String =
    if (useFullBody) article.body else (article.summary ?: article.body)

fun shouldUseFullBody(articleCount: Int, podcast: Podcast, defaultThreshold: Int): Boolean =
    articleCount < (podcast.fullBodyThreshold ?: defaultThreshold)

fun extractDomain(url: String): String =
    try {
        URI(url).host?.removePrefix("www.") ?: url
    } catch (_: Exception) {
        url
    }

fun buildArticleSummaryBlock(
    articles: List<Article>,
    useFullBody: Boolean,
    followUpAnnotations: Map<Long, String> = emptyMap(),
    articleSubtopics: Map<Long, String> = emptyMap()
): String {
    val groupedByFollowUp = mutableMapOf<String?, MutableList<Pair<Int, Article>>>()
    articles.forEachIndexed { index, article ->
        val context = article.id?.let { followUpAnnotations[it] }
        groupedByFollowUp.getOrPut(context) { mutableListOf() }.add(index to article)
    }

    return groupedByFollowUp.entries.joinToString("\n\n") { (context, articlePairs) ->
        val header = context?.let { "[FOLLOW-UP: $it]\n" } ?: ""
        val block = articlePairs.joinToString("\n\n") { (index, article) ->
            val source = extractDomain(article.url)
            val authorSuffix = article.author?.let { ", by $it" } ?: ""
            val subtopicTag = article.id?.let { articleSubtopics[it] }?.let { " [subtopic: $it]" } ?: ""
            val content = resolveArticleContent(article, useFullBody)
            "${index + 1}. [$source$authorSuffix]$subtopicTag ${article.title}\n$content"
        }
        "$header$block"
    }
}

/**
 * Shared humor-and-tone rule for every compose-stage prompt. Placed as the FIRST engagement
 * bullet (not appended to the sign-off, where it reads as a sign-off-only instruction) so it
 * frames the whole episode. Deliberately concrete and countable, mirroring the interruptions
 * rule: vague "be playful" phrasing loses against the prompt's many strict structural rules.
 * On Fridays an extra beat of end-of-week energy is requested; the end of the week may only
 * be acknowledged conversationally, never as a "Happy Friday" style shout-out (listener
 * feedback: too much).
 */
fun buildHumorBlock(): String {
    val fridayExtra = if (LocalDate.now().dayOfWeek == DayOfWeek.FRIDAY) " Today is FRIDAY: add one extra humorous beat and let the energy run a notch higher. You may acknowledge the end of the week, but only conversationally and in passing (\"It's the end of the week...\", \"What a week...\"); never as a direct greeting or shout-out like \"Happy Friday\"." else ""
    return "\n            - HUMOR & TONE: The vibe is relaxed and playful throughout: colleagues who genuinely enjoy the subject, not news anchors reading a wire feed. Include 2-3 genuine moments of humor per episode, each tied to a specific story, never generic filler. This is a HARD REQUIREMENT, like the interruption count. Vary the flavour across these categories: an absurd or everyday comparison, a playful exaggeration, a self-deprecating aside about the hosts or the AI field itself, or a deadpan one-liner. Land each joke in one or two sentences and move on; never explain the joke or let it derail the segment. Keep humor away from genuinely serious or negative stories.$fridayExtra"
}

fun buildCurrentDate(language: String): String {
    val locale = SupportedLanguage.fromCode(language)?.toLocale() ?: Locale.ENGLISH
    return LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", locale))
}

fun buildCustomInstructionsBlock(customInstructions: String?): String =
    customInstructions?.let { "\n\nAdditional instructions: $it" } ?: ""

fun buildLanguageInstruction(language: String, scriptType: String): String =
    if (language != "en") {
        val langName = SupportedLanguage.fromCode(language)?.displayName ?: "English"
        "\n            - Write the entire $scriptType in $langName"
    } else ""

fun buildSponsorBlock(sponsor: Map<String, String>?, speakerPrefix: String = ""): String =
    sponsor?.let { s ->
        val name = s["name"] ?: return@let ""
        val message = s["message"] ?: return@let ""
        """
            - Immediately after the introduction, ${speakerPrefix}include the sponsor message: "This podcast is brought to you by $name, $message."
            - End with a sign-off that includes a mention of the sponsor: $name"""
    } ?: ""

fun buildTtsGuidelinesBlock(ttsScriptGuidelines: String): String =
    if (ttsScriptGuidelines.isNotEmpty()) {
        "\n\n            TTS script formatting:\n            $ttsScriptGuidelines"
    } else ""

fun buildTopicOrderBlock(topicLabels: List<String>): String {
    if (topicLabels.isEmpty()) return ""
    val labelList = topicLabels.joinToString("\n") { "- $it" }
    return """

            Topic ordering metadata:
            After writing the complete script, append the following metadata block on a new line. List the topic labels below in the order they are discussed in the script. Use the EXACT labels provided, do not rename or rephrase them.

            Topics:
            $labelList

            Format:
            |||TOPIC_ORDER|||
            ["first topic discussed", "second topic discussed", ...]
            |||END_TOPIC_ORDER|||"""
}

/**
 * Prompt block instructing the LLM to consult prior-episode coverage via the
 * `searchPastEpisodes` tool before treating any subject as new. Included in every
 * compose-stage prompt; the tool itself is registered by `ChatClientFactory.createForCompose`.
 */
fun buildHistoryLookupBlock(): String = """
            - HISTORY CHECK: Before treating any subject as new, call the `searchPastEpisodes` tool with one or two keywords (e.g. ${'"'}speckit${'"'}, ${'"'}OpenAI o3${'"'}). If the tool returns a prior episode that covered the topic, either skip it, treat it as a follow-up referencing the prior coverage, or angle the segment as an update. You have a small budget for these lookups; spend them on topics most likely to have been covered before"""

/**
d * Prompt block instructing the LLM to use the `webSearch` Tavily tool to enrich the 2-3 most
 * newsworthy stories with outside context, within the episode-wide call budget. Empty when
 * [deepDiveEnabled] is false; in that case the tool is not registered at all and the LLM must
 * not be hinted to call it.
 */
fun buildWebSearchBlock(deepDiveEnabled: Boolean, hasSubtopics: Boolean = false): String {
    if (!deepDiveEnabled) return ""
    if (!hasSubtopics) return """
            - DEEP DIVE: Identify the 2-3 most newsworthy stories in this episode and call the `webSearch` tool roughly once each to pull outside context (background, related developments, dissenting takes). Weave the snippets into those segments with proper attribution. You have an episode-wide budget of 3 calls total; aim to spend 2-3 of them across the standout stories rather than all on one"""
    return """
            - DEEP DIVE: Use the `webSearch` tool ONLY for stories you will cover in a full segment, never for rapid-fire stories (a one-sentence mention cannot absorb fetched context). Pick the 2-3 most newsworthy full-segment stories, preferring higher-weight subtopics when stories are comparable, and call `webSearch` roughly once each to pull outside context (background, related developments, dissenting takes). Weave the snippets into those segments with proper attribution. You have an episode-wide budget of 3 calls total; aim to spend 2-3 of them across the standout stories rather than all on one, and never multiply it by the number of subtopics"""
}

/**
 * Shared punctuation rule for every compose-stage prompt: forbids em-dash and en-dash
 * characters, which the TTS engines mispronounce. Included verbatim in briefing, dialogue,
 * and interview prompts so the rule lives in one place.
 */
fun buildPunctuationBlock(): String =
    "\n            - PUNCTUATION: Do NOT use em-dashes (—) or en-dashes (–) anywhere in the script. Use commas, colons, parentheses, or short sentences instead. The TTS engine mispronounces dash characters."

/**
 * Shared "pace the opening" rule for every compose-stage prompt. The hook/cold-open tends to be
 * written as one long, comma-stacked run-on sentence, which the TTS engine reads at a noticeably
 * faster clip than the short conversational turns that follow, so the intro sounds rushed relative
 * to the rest of the episode. This block tells the LLM to write the opening as a few short
 * sentences with full-stop (and occasional ellipsis) pacing beats. Included verbatim in briefing,
 * dialogue, and interview prompts so the rule lives in one place.
 */
fun buildColdOpenPacingBlock(): String =
    "\n            - OPENING PACING: Write the hook/cold-open as a few short, punchy sentences, NOT one long comma-stacked run-on. Use full stops as pacing beats (and an occasional ... for a deliberate pause) so the TTS engine does not rush the opening and its spoken pace stays even with the rest of the episode."

/**
 * Shared "no empty setup" rule for multi-speaker (dialogue/interview) compose prompts. The model
 * likes conversational handoffs and sometimes writes a contentless setup turn — one speaker
 * announces a point ("One small skeptical flag though.") and the OTHER speaker supplies the actual
 * substance, leaving a dangling, incoherent gap. This block forbids that: whoever teases a point
 * must state it in the same turn. Only included in dialogue and interview prompts (monologue has
 * no handoff).
 */
fun buildNoEmptySetupBlock(): String =
    "\n            - NO EMPTY SETUP TURNS: When a speaker announces or teases a specific point (a caveat, skeptical flag, question, fact, or statistic), that SAME speaker must state its substance in the same turn. Do NOT write a contentless setup line (e.g. \"One small skeptical flag though.\") and then have the other speaker supply the actual point — that is a dangling, incoherent handoff. A handoff is fine only when the next speaker adds genuinely new information, never when they complete a point the first speaker merely gestured at."

/**
 * Shared "explain for non-experts" rule for every compose-stage prompt. The audience is not
 * all specialists, so for complex or unfamiliar subjects the script is allowed (and encouraged)
 * to slow down and explain what something is, how it works, or why it matters, rather than
 * rushing past it. Pairs with the deep-dive / webSearch tool: when outside context is needed to
 * make a topic understandable, the model may pull it in (within budget). Included verbatim in
 * briefing, dialogue, and interview prompts so the rule lives in one place.
 */
fun buildAudienceBlock(): String =
    "\n            - EXPLAIN FOR NON-EXPERTS: This is cutting-edge material and not every listener already follows each subject. Whenever a genuinely complex or unfamiliar concept comes up, take a moment to explain it clearly in plain language before moving on: what it is, how it works at a high level, and why it matters (the consequences). Define jargon the first time it appears and reach for an everyday analogy when it helps. Do not go super in-depth or turn it into a lecture: aim for just enough that a non-specialist understands what is being discussed, which also gives advanced listeners a beat to absorb the information. Keep each explanation brief, and prioritise the concepts that genuinely warrant it over cramming in another story. When a topic genuinely needs outside context to make sense and a web search tool is available, use it (within budget) to enrich the explanation."

/**
 * Shared "numbers for the ear" rule for every compose-stage prompt. Dense benchmark scores,
 * percentages, and parameter counts read fine on a page but overwhelm a listener when spoken.
 * This block tells the LLM to round, frame, and ration numbers so stories stay digestible in
 * audio without losing their meaning. Included verbatim in briefing, dialogue, and interview
 * prompts so the rule lives in one place.
 */
fun buildNumbersBlock(): String =
    "\n            - NUMBERS FOR THE EAR: Spoken numbers overwhelm listeners far faster than written ones. Speak figures the way a person would aloud: round to a clean value (\"70.06%\" becomes \"about 70 percent\", \"11,039 tests\" becomes \"over 11,000\"). Lead with what a result MEANS, then the number, and prefer plain-language comparisons (\"nearly half\", \"doubled\", \"three times faster\") over raw decimals. Voice AT MOST ONE number per sentence or claim: never stack two or three stats in a single breath. When a story carries several metrics, speak only the one that matters most and summarize the rest qualitatively. De-emphasize benchmark proper-names: describe what a benchmark measures (\"a coding benchmark\", \"a computer-use test\") rather than reciting an exact name and score together, unless the name itself is the news."

/**
 * Shared "spoken names for the ear" rule for every compose-stage prompt. AI model, product,
 * software package, repository, and domain names are full of hyphens, slashes, dots,
 * abbreviations, and version numbers that the TTS engine mispronounces (reading the punctuation
 * aloud, spelling out letter clusters). This block tells the LLM to write such names the way a
 * person says them, so the audio sounds natural. Included verbatim in briefing, dialogue, and
 * interview prompts so the rule lives in one place.
 */
fun buildModelNamesBlock(): String =
    "\n            - MODEL, PRODUCT & PACKAGE NAMES: Write AI model, product, software package, repository, and domain names the way a person says them aloud, not as written on a page. Replace hyphens, slashes, and dots with natural spoken words or pauses, speak version numbers and standalone digits as words, and expand letter-clusters to how they actually sound. For example \"MAI-Code-1-Flash\" is spoken \"May Code One Flash\", \"GPT-4o\" is \"GPT four oh\", \"Claude 3.5 Sonnet\" is \"Claude three point five Sonnet\", the package \"datasette-agent-micropython\" becomes \"the Datasette agent for MicroPython\", and \"warp.dev\" is simply \"Warp\". Otherwise the TTS engine reads the hyphens, slashes, and dots aloud and spells out the letters."

/**
 * Shared "source names, not handles" rule for every compose-stage prompt. The composer often cites
 * sources by their raw social-media handle (an X or GitHub username), which the TTS engine cannot
 * voice naturally and sometimes even feeds to the phoneme engine. This block tells the LLM to use
 * real names or generic descriptors instead, and reinforces that the IPA slash notation belongs
 * only to listed pronunciation terms. Included verbatim in briefing, dialogue, and interview
 * prompts so the rule lives in one place.
 */
fun buildHandlesBlock(): String =
    "\n            - SOURCE NAMES, NOT HANDLES: Never read social-media usernames or handles aloud (e.g. an X or GitHub handle like \"@hwchase17\" or \"trq212\"). When you know the real person or organization behind a handle, use their real name; otherwise attribute generically (\"a developer on X\", \"the project's maintainer\"). Do NOT wrap handles in slashes or any phoneme notation: the IPA slash notation is reserved exclusively for the listed pronunciation-guide terms."

/**
 * Shared "research names for the ear" rule for every compose-stage prompt. The compose stage tends
 * to recite a rapid list of paper codenames plus author surnames ("X from Smith and colleagues"),
 * which fatigues a listener. This block tells the LLM to lead with what the research does and to
 * ration unfamiliar proper names. Included verbatim in briefing, dialogue, and interview prompts so
 * the rule lives in one place.
 */
fun buildResearchNamesBlock(): String =
    "\n            - RESEARCH NAMES FOR THE EAR: A spoken episode cannot absorb a rapid list of paper codenames and author surnames. Lead with what a piece of research DOES, and voice its codename only when the name itself is the news. Do not stack author attributions like \"X from Smith and colleagues\" on every paper: drop or soften the surnames (at most credit a notable lab or company), and never recite more than one unfamiliar proper name per sentence."

private val SPEAKER_TURN_PATTERN = Regex("<(\\w+)>.*?</\\1>", RegexOption.DOT_MATCHES_ALL)

/**
 * Strips any text before the first opening speaker tag and after the last closing speaker tag.
 * The compose LLM tends to "think out loud" after its last tool call (e.g. "I have enough
 * context. Writing the script now.") before emitting the tagged script, despite the prompt
 * forbidding text outside speaker tags. The TTS parser already ignores such text, but it must
 * not be stored in the episode script (it shows in the dashboard and pollutes word counts).
 * Scripts without any speaker tags (briefing style) are returned unchanged.
 */
fun stripOutsideSpeakerTags(script: String): String {
    val turns = SPEAKER_TURN_PATTERN.findAll(script).toList()
    if (turns.isEmpty()) return script
    return script.substring(turns.first().range.first, turns.last().range.last + 1)
}

private val META_PREAMBLE_PATTERN = Regex(
    "(?i)\\b(?:writ(?:e|ing)|draft(?:ing)?|compos(?:e|ing))\\b[^.]*\\bscript\\b" +
        "|\\bscript\\b[^.]*\\b(?:now|next)\\b" +
        "|\\b(?:enough|plenty of|the full|all the) (?:context|information)\\b" +
        "|\\bwhat i need\\b"
)

/**
 * Strips a leading meta-commentary paragraph from a monologue script (e.g. "I have enough
 * context. Writing the script now."). Monologue scripts have no speaker tags, so the whole
 * text reaches TTS verbatim and a leaked preamble would be read aloud. Conservative on
 * purpose: only the FIRST paragraph is considered, and only when it is short and matches
 * known "I'm about to write" phrasings, so genuine spoken openings are never dropped.
 */
fun stripLeadingMetaCommentary(script: String): String {
    val trimmed = script.trimStart()
    val paragraphEnd = trimmed.indexOf("\n")
    if (paragraphEnd == -1) return script
    val firstParagraph = trimmed.substring(0, paragraphEnd).trim()
    if (firstParagraph.length <= 300 && META_PREAMBLE_PATTERN.containsMatchIn(firstParagraph)) {
        return trimmed.substring(paragraphEnd).trimStart()
    }
    return script
}

fun extractDomainAndPath(url: String): String =
    try {
        val uri = URI(url)
        val domain = uri.host?.removePrefix("www.") ?: return url
        val path = uri.path?.trimEnd('/') ?: ""
        if (path.isEmpty()) domain else "$domain$path"
    } catch (_: Exception) {
        url
    }
