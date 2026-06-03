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

fun buildToneBlock(): String {
    val fridayExtra = if (LocalDate.now().dayOfWeek == DayOfWeek.FRIDAY) " It's Friday! Extra energy, wrap up the week with friends over drinks." else ""
    return "\n            - Go loose and have fun with it. Be playful, crack jokes, use humor freely, riff on the topics. Let the energy be high and the vibe relaxed.$fridayExtra"
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
 * Prompt block instructing the LLM to use the `webSearch` Tavily tool to enrich the most
 * newsworthy story with outside context. Empty when [deepDiveEnabled] is false; in that
 * case the tool is not registered at all and the LLM must not be hinted to call it.
 */
fun buildWebSearchBlock(deepDiveEnabled: Boolean, hasSubtopics: Boolean = false): String {
    if (!deepDiveEnabled) return ""
    if (!hasSubtopics) return """
            - DEEP DIVE: Identify the SINGLE most newsworthy story in this episode and call the `webSearch` tool 1-2 times to pull outside context (background, related developments, dissenting takes). Weave the snippets into that segment with proper attribution. You have a small budget; use it only on the standout story"""
    return """
            - DEEP DIVE: Use the `webSearch` tool ONLY for stories you will cover in a full segment, never for rapid-fire stories (a one-sentence mention cannot absorb fetched context). Pick the most newsworthy story among the full-segment subtopics, preferring higher-weight subtopics when stories are comparable, and call `webSearch` 1-2 times to pull outside context (background, related developments, dissenting takes). Weave the snippets into that segment with proper attribution. You have a small episode-wide budget of 3 calls total; do not multiply it by the number of subtopics"""
}

/**
 * Shared punctuation rule for every compose-stage prompt: forbids em-dash and en-dash
 * characters, which the TTS engines mispronounce. Included verbatim in briefing, dialogue,
 * and interview prompts so the rule lives in one place.
 */
fun buildPunctuationBlock(): String =
    "\n            - PUNCTUATION: Do NOT use em-dashes (—) or en-dashes (–) anywhere in the script. Use commas, colons, parentheses, or short sentences instead. The TTS engine mispronounces dash characters."

/**
 * Shared "explain for non-experts" rule for every compose-stage prompt. The audience is not
 * all specialists, so for complex or unfamiliar subjects the script is allowed (and encouraged)
 * to slow down and explain what something is, how it works, or why it matters, rather than
 * rushing past it. Pairs with the deep-dive / webSearch tool: when outside context is needed to
 * make a topic understandable, the model may pull it in (within budget). Included verbatim in
 * briefing, dialogue, and interview prompts so the rule lives in one place.
 */
fun buildAudienceBlock(): String =
    "\n            - EXPLAIN FOR NON-EXPERTS: Not every listener is a specialist. For a complex or unfamiliar subject, it is GOOD to spend a few extra sentences explaining what it is, how it works, or why it matters (the consequences) before moving on. A clear plain-language explanation of one important topic is worth more than cramming in another story. Use everyday analogies, define jargon the first time it appears, and when a topic genuinely needs outside context to make sense and a web search tool is available, use it (within budget) to enrich the explanation. Keep it conversational, not a lecture, and reserve this depth for topics that actually warrant it."

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

fun extractDomainAndPath(url: String): String =
    try {
        val uri = URI(url)
        val domain = uri.host?.removePrefix("www.") ?: return url
        val path = uri.path?.trimEnd('/') ?: ""
        if (path.isEmpty()) domain else "$domain$path"
    } catch (_: Exception) {
        url
    }
