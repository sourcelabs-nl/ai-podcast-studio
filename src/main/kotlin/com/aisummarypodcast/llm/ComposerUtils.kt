package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.podcast.SupportedLanguage
import com.aisummarypodcast.research.PreComposeResearch
import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.Podcast
import org.slf4j.LoggerFactory
import org.springframework.ai.openai.OpenAiChatOptions
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
 *
 * The weekday comes from [episodeDate], the day the episode covers, not from the day the run
 * happens: a re-run of Wednesday's episode on Friday is still a Wednesday episode.
 *
 * [multiSpeaker] adds the rule that humor is shared work. Episode 208 met the count with three
 * beats and still read as one person being funny at another: every joke was the interviewer's, and
 * each was a prepared aside rather than a reaction. A long-form two-host reference show gets its
 * laughs the other way round — teasing the co-host, self-deprecation, reacting to the absurdity of
 * the item just described — and every one of them grows out of the line immediately before it. The
 * flavour menu below describes shapes of line, which is why it produces delivered lines; these two
 * extra requirements constrain WHO speaks and WHAT the joke answers to. Monologue formats pass
 * false, having no other speaker to react to.
 */
fun buildHumorBlock(episodeDate: LocalDate = LocalDate.now(), multiSpeaker: Boolean = false): String {
    val fridayExtra = if (episodeDate.dayOfWeek == DayOfWeek.FRIDAY) " Today is FRIDAY: add one extra humorous beat and let the energy run a notch higher. You may acknowledge the end of the week, but only conversationally and in passing (\"It's the end of the week...\", \"What a week...\"); never as a direct greeting or shout-out like \"Happy Friday\"." else ""
    val shared = if (multiSpeaker) " Humor is NOT one speaker's job: at least one beat MUST come from a speaker other than the one who opens the episode, and at least one beat MUST be a direct reaction to what the other speaker just said, growing out of their line rather than a prepared aside dropped into a turn." else ""
    return "\n            - HUMOR & TONE: The vibe is relaxed and playful throughout: colleagues who genuinely enjoy the subject, not news anchors reading a wire feed. Include 2-3 genuine moments of humor per episode, each tied to a specific story, never generic filler. This is a HARD REQUIREMENT, like the interruption count. Vary the flavour across these categories: an absurd or everyday comparison, a playful exaggeration, a self-deprecating aside about the hosts or the AI field itself, or a deadpan one-liner. Land each joke in one or two sentences and move on; never explain the joke or let it derail the segment. Keep humor away from genuinely serious or negative stories.$shared$fridayExtra"
}

/**
 * The date the episode is about, as the composer states it in the script. This is [episodeDate], the
 * day the episode's article window ends, never the day the run happens: a re-run or a regeneration
 * of a past day announces that day.
 */
fun buildEpisodeDate(language: String, episodeDate: LocalDate = LocalDate.now()): String {
    val locale = SupportedLanguage.fromCode(language)?.toLocale() ?: Locale.ENGLISH
    return episodeDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", locale))
}

/**
 * Prompt block telling the composer when the show is next on air, so the sign-off can promise the
 * right day.
 *
 * Nothing in the prompt used to state the schedule: the composer knew only the episode's own date
 * and a sign-off shape, so it guessed. A Wednesday episode of a Monday-to-Friday show closed with
 * "we'll be back next week". The day comes from the podcast's cron
 * (`EpisodeWindowResolver.nextEpisodeDateAfter`) and is phrased relative to [episodeDate], because
 * "tomorrow" and "Monday" are what a host actually says.
 *
 * Empty when the next slot is unknown (an unparseable or very infrequent cron). An empty block is
 * deliberately not a silent fallback to some default wording: with no block the model is free as
 * before, which is better than a confidently wrong date.
 */
fun buildNextEpisodeBlock(language: String, episodeDate: LocalDate, nextEpisodeDate: LocalDate?): String {
    if (nextEpisodeDate == null || !nextEpisodeDate.isAfter(episodeDate)) return ""
    val locale = SupportedLanguage.fromCode(language)?.toLocale() ?: Locale.ENGLISH
    val weekday = nextEpisodeDate.format(DateTimeFormatter.ofPattern("EEEE", locale))
    val relative = if (nextEpisodeDate == episodeDate.plusDays(1)) "tomorrow ($weekday)" else weekday
    return "\n            - WHEN THE SHOW IS BACK: The next episode is $relative. If the sign-off says when you will be back, say $relative and nothing else. Do NOT promise a different interval (\"next week\", \"in a few days\", \"same time next week\") and do NOT invent a publishing schedule: this show does not run on the cadence you might assume. Saying nothing about the next episode is also fine; naming the wrong day is not"
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
 * The rule on what counts as new, and, when the research stage found past episodes matching this
 * episode's subjects, how to use them. The matches are deliberately subordinate to the
 * `[FOLLOW-UP: ...]` headers: those come from the dedup stage, which compared every candidate's
 * title and summary against the actual set of historical episode articles, while the history
 * search matches keywords against past scripts and so reports a hit whenever a product name recurs
 * in a different story. An earlier version told the model to treat any hit as prior coverage, and a
 * regeneration of episode 197 duly claimed the GPT-6 Astra launch had been "covered yesterday" on
 * three keyword matches, when the previous episode never mentioned it and the older match was a
 * pre-release benchmark story. It demoted the day's lead story on that basis.
 */
fun buildHistoryGuidanceBlock(research: PreComposeResearch): String {
    val whatCountsAsNew = """
            - WHAT COUNTS AS NEW: The `[FOLLOW-UP: ...]` headers above the article groups are authoritative. An article group carrying such a header continues a story from an earlier episode, and you may reference that prior coverage. An article group with NO header is new: cover it as news, and keep it eligible to lead the episode"""
    if (research.history.isEmpty()) return whatCountsAsNew
    return whatCountsAsNew + """
            - PREVIOUSLY COVERED: The "Previously covered" section below lists past episodes of this podcast whose recaps match today's subjects. Use it to see how a recurring subject was framed before, so you can reference it accurately and avoid reusing the same phrasing, examples or statistics. Use it for wording, not for the running order. A match is NOT evidence that today's development was already covered: do NOT skip a story, demote it out of the lead, or claim the audience already heard it, unless it carries a `[FOLLOW-UP: ...]` header saying so. Never assert when a topic was previously covered unless the header states it
            - FOCUS EPISODE MATCHES: A match marked [focus episode] is a special episode that covered one subject in depth. Do NOT skip that subject because of it: treat it as a continuation, lead with what is new since that episode, and you may reuse a few of the same articles where they support the new developments rather than avoiding them entirely"""
}

/**
 * How to use the web research the research stage ran before compose. Empty when it found nothing,
 * which includes every run where web search does not apply. A focus episode spends its research on
 * the one subject the episode is about; a podcast with subtopics keeps outside context out of the
 * rapid-fire stories, where a one-sentence mention cannot absorb it.
 */
fun buildResearchGuidanceBlock(context: ComposeContext, hasSubtopics: Boolean): String {
    if (context.research.sources.isEmpty()) return ""
    if (context.focus != null) return """
            - DEEP DIVE: This episode is about a single subject, so use the "Background research" section below properly: weave its context (the announcement itself, reactions, benchmarks or numbers, what it means for the field) into the script with proper attribution, drawing on several of its sources rather than one"""
    if (!hasSubtopics) return """
            - DEEP DIVE: The "Background research" section below holds outside context (background, related developments, dissenting takes) on the most newsworthy stories. Weave the relevant snippets into those segments with proper attribution; ignore results that do not fit a story you cover"""
    return """
            - DEEP DIVE: The "Background research" section below holds outside context (background, related developments, dissenting takes) on the most newsworthy stories. Use it ONLY in stories you cover in a full segment, never in rapid-fire stories (a one-sentence mention cannot absorb fetched context). Weave the relevant snippets into those segments with proper attribution; ignore results that do not fit a story you cover"""
}

private val WHITESPACE_RUN = Regex("\\s+")

/**
 * The research stage's findings as data sections placed after the article summaries: web search
 * results with their source, and matching past episodes with their date and recap. Each section is
 * omitted when it is empty, so the guidance above never points at a section that is not there.
 */
fun buildResearchDataBlock(research: PreComposeResearch): String = buildString {
    if (research.sources.isNotEmpty()) {
        append("\n\n            Background research (web search results gathered for this episode):")
        research.sources.forEachIndexed { index, source ->
            append("\n            ${index + 1}. ${source.title} (${extractDomain(source.url)}, ${source.url})")
            append("\n            ${source.snippet.replace(WHITESPACE_RUN, " ").trim()}")
        }
    }
    if (research.history.isNotEmpty()) {
        append("\n\n            Previously covered (past episodes of this podcast matching today's subjects):")
        for (match in research.history) {
            val marker = if (match.isFocusEpisode) " [focus episode]" else ""
            val topics = match.topics.ifBlank { "(none recorded)" }
            append("\n            - ${match.generatedAt}$marker: topics $topics. ${match.recapSnippet}")
        }
    }
}

/**
 * The run-specific instructions a compose prompt carries beyond the podcast's own settings: the
 * subject of a focus episode, the focus episodes a regular episode should follow up on, and a
 * reviewer's feedback on the previous script. Empty for an ordinary regular episode.
 */
fun buildRunContextBlock(context: ComposeContext): String = buildString {
    context.focus?.let { focus ->
        append("\n\n            Focus episode: this is a special episode about one subject only: \"$focus\". ")
        append("Every article below was selected for its relevance to that subject. Build the whole episode around it, ")
        append("In the introduction say clearly that this is an extra, special episode on top of the regular episodes, about this one subject. ")
        append("In the closing, say again that this was a special episode and that the regular episode follows as usual.")
    }
    if (context.recentFocusEpisodes.isNotEmpty()) {
        append("\n\n            Recent focus episodes: since the previous regular episode, this podcast aired special episodes on:")
        for (episode in context.recentFocusEpisodes) {
            append("\n            - \"${episode.focus}\" (${episode.generatedAt.take(10)})")
        }
        append("\n            If today's articles touch one of these subjects, treat it as a follow-up (the \"Previously covered\" section shows how it was framed, when it matched): ")
        append("lead with what is new since that episode instead of repeating it, and do not drop the subject just because it was covered.")
    }
    context.extraInstruction?.takeIf { it.isNotBlank() }?.let { feedback ->
        append("\n\n            Reviewer feedback on the previous version of this script, which this version MUST address: $feedback")
        append("\n            If this feedback asks for a different length, it overrides the target word count above.")
    }
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
 * Shared "no echo turns" rule for multi-speaker (dialogue/interview) compose prompts. Passing the
 * floor back with a short reaction is wanted, but the model sometimes writes that reaction as a
 * bare repeat of the words it just heard: episode 219 answered the introduction's "Let's get into
 * it." with an expert turn reading, in full, "Let's." That spends a speaker switch on nothing and,
 * because each turn is synthesized on its own, lands as two stranded syllables in the audio. This
 * block keeps the handoff and requires the reaction to carry its own words and energy. Only
 * included in dialogue and interview prompts (monologue has no handoff).
 */
fun buildNoEchoTurnBlock(): String =
    "\n            - NO ECHO TURNS: A turn must never consist of words echoed back from the line it follows. Answering \"Let's get into it.\" with \"Let's.\" is not a reaction, it is an echo, and it wastes a speaker switch. Short reaction turns are welcome, but outside the BACKCHANNELS device every turn is at least one complete sentence carrying its own content or its own energy: \"Let's do this!\" or \"Oh, I've been waiting all week for this one.\", never a bare fragment lifted from the previous speaker."

/**
 * Shared "explain for non-experts" rule for every compose-stage prompt. The audience is not
 * all specialists, so for complex or unfamiliar subjects the script is allowed (and encouraged)
 * to slow down and explain what something is, how it works, or why it matters, rather than
 * rushing past it. Pairs with the background research: when outside context is needed to make a
 * topic understandable, the model may draw on it. Included verbatim in
 * briefing, dialogue, and interview prompts so the rule lives in one place.
 */
fun buildAudienceBlock(): String =
    "\n            - EXPLAIN FOR NON-EXPERTS: This is cutting-edge material and not every listener already follows each subject. Whenever a genuinely complex or unfamiliar concept comes up, take a moment to explain it clearly in plain language before moving on: what it is, how it works at a high level, and why it matters (the consequences). Define jargon the first time it appears and reach for an everyday analogy when it helps. Do not go super in-depth or turn it into a lecture: aim for just enough that a non-specialist understands what is being discussed, which also gives advanced listeners a beat to absorb the information. Keep each explanation brief, and prioritise the concepts that genuinely warrant it over cramming in another story. When a topic genuinely needs outside context to make sense and the \"Background research\" section covers it, use that context to enrich the explanation."

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
    "\n            - MODEL, PRODUCT & PACKAGE NAMES: Write AI model, product, software package, repository, and domain names the way a person says them aloud, not as written on a page. Replace hyphens, slashes, and dots with natural spoken words or pauses, speak version numbers and standalone digits as words, and expand letter-clusters to how they actually sound. For example \"MAI-Code-1-Flash\" is spoken \"May Code One Flash\", \"GPT-4o\" is \"GPT four oh\", \"Claude 3.5 Sonnet\" is \"Claude three point five Sonnet\", the package \"datasette-agent-micropython\" becomes \"the Datasette agent for MicroPython\", and \"warp.dev\" is simply \"Warp\". Keep an initialism in capitals (\"SWE two\", \"GPT four oh\", \"IBM\"): the capitals are what make the engine spell the letters out, and title-casing one turns it into a word, which is how \"SWE-2\" became the spoken word \"swea two\". Otherwise the TTS engine reads the hyphens, slashes, and dots aloud and spells out the letters."

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

private val log = LoggerFactory.getLogger("com.aisummarypodcast.llm.ComposerUtils")

internal val SPEAKER_TURN_PATTERN = Regex("<(\\w+)>.*?</\\1>", RegexOption.DOT_MATCHES_ALL)

/**
 * Shared rule on speaker-tag format for multi-speaker scripts, covering both ways the tags go
 * wrong. The model sometimes carries the prompt's dense square-bracketed delivery cues into a
 * speaker tag, and it sometimes drops the tags entirely and writes the turns as plain alternating
 * paragraphs (episode 187), which leaves the script unusable for TTS since every turn is invisible
 * to the parser. Included verbatim in the dialogue and interview prompts so the rule lives in one
 * place.
 */
fun buildSpeakerTagFormatBlock(roles: Set<String>): String {
    val example = roles.firstOrNull() ?: "host"
    return "\n            - SPEAKER TAGS ARE MANDATORY: EVERY line of spoken text must be wrapped in " +
        "a speaker tag, including the cold open and the sign-off. The only valid tags are " +
        "${roles.joinToString { "<$it>…</$it>" }}. A script written as plain alternating paragraphs " +
        "without tags cannot be voiced at all and is discarded, no matter how good the writing is." +
        "\n            - SPEAKER TAG DELIMITERS: Speaker tags use angle brackets on BOTH sides. " +
        "Write <$example>…</$example>, never [$example]…</$example> and never [$example]…[/$example]. " +
        "Square brackets are reserved for delivery cues inside a turn, so a square-bracketed speaker " +
        "tag is not recognised as a turn at all and that turn is lost."
}

/**
 * Rewrites a square-bracketed speaker opener (`[expert] … </expert>`) into a well-formed
 * `<expert> … </expert>` turn, for the [roles] this podcast actually uses.
 *
 * [SPEAKER_TURN_PATTERN] requires an angle-bracket opener, which makes a turn opened with a square
 * bracket invisible to every downstream step: [stripOutsideSpeakerTags] reads it as text sitting
 * before the script and drops it, and [RoleTagValidationAdvisor] never sees a tag to object to, so
 * nothing warns. Episode 184 lost its entire cold open and introduction to a single mis-typed
 * bracket, on a turn the model had otherwise written correctly and closed with `</interviewer>`.
 *
 * Deliberately narrow. Only [roles] are considered, and an opener is rewritten only when the very
 * next `<` in the script begins its own closing tag. A speaker turn's body carries no tags, so that
 * condition identifies the mis-typed opener while leaving a genuine delivery cue such as
 * `[warm and conversational]` alone and refusing to swallow a later, well-formed turn.
 */
fun normalizeSquareBracketSpeakerTags(script: String, roles: Set<String>): String {
    var result = script
    for (role in roles) {
        val opener = "[$role]"
        val closer = "</$role>"
        var searchFrom = 0
        while (true) {
            val openerAt = result.indexOf(opener, searchFrom)
            if (openerAt == -1) break
            val bodyStart = openerAt + opener.length
            val closerAt = result.indexOf(closer, bodyStart)
            if (closerAt != -1 && result.indexOf('<', bodyStart) == closerAt) {
                result = result.replaceRange(openerAt, bodyStart, "<$role>")
                log.warn("Compose LLM opened a <{}> turn with a square bracket; recovered {} characters", role, closerAt - bodyStart)
                searchFrom = openerAt + role.length + 2
            } else {
                searchFrom = bodyStart
            }
        }
    }
    return result
}

/** Every bare `<tag>` or `</tag>` in a script. Delivery markup carries attributes and is not matched. */
private val TAG_TOKEN_PATTERN = Regex("</?(\\w+)>")

/**
 * Rewrites a closing speaker tag that does not match the turn it closes, for the [roles] this
 * podcast actually uses.
 *
 * [SPEAKER_TURN_PATTERN] requires the closer to name the same role as the opener, and it matches
 * lazily across newlines, so `<expert>…</interviewer>` does not fail: the match simply runs on to
 * the next `</expert>`, swallowing every turn in between into one giant expert turn. Nothing
 * downstream can see that happened. [RoleTagValidationAdvisor] reads the role off the opener and
 * finds it valid, and the TTS parser voices the whole run in one voice.
 *
 * That is what happened to episode 222. Two turns were closed with the wrong role and one with a
 * misspelled `</epxert>`, and from that point the whole back half of the episode was attributed to
 * the wrong speaker: the expert asked the questions and the interviewer explained what a KV cache
 * is.
 *
 * The opener is treated as authoritative, because it sits next to the previous turn's closer and
 * therefore in the run of text whose speaker is already established, while the closer is the tag
 * the model demonstrably got wrong. The reverse reading (the opener is wrong, the closer right)
 * cannot be ruled out for any single turn, so every rewrite is logged at WARN with both roles.
 *
 * Deliberately narrow, like the other repairs here: only a closer directly following an opener of a
 * known role is rewritten. An opener that never closes at all is left to
 * [closeUnterminatedFinalTurn] and, failing that, to validation.
 */
fun repairMismatchedTurnClosers(script: String, roles: Set<String>): String {
    val tokens = TAG_TOKEN_PATTERN.findAll(script).toList()
    val rewrites = mutableListOf<Pair<IntRange, String>>()
    var openRole: String? = null

    for (token in tokens) {
        val role = token.groupValues[1]
        val isCloser = token.value.startsWith("</")

        if (!isCloser) {
            // An opener while a turn is still open means a missing closer, not a wrong one.
            openRole = role.takeIf { it in roles }
            continue
        }

        val expected = openRole ?: continue
        if (role != expected) {
            rewrites.add(token.range to "</$expected>")
            log.warn("Compose LLM closed a <{}> turn with </{}>; rewrote the closer", expected, role)
        }
        openRole = null
    }

    if (rewrites.isEmpty()) return script

    // Applied back to front so an earlier rewrite cannot shift a later range.
    var result = script
    for ((range, replacement) in rewrites.asReversed()) {
        result = result.replaceRange(range, replacement)
    }
    return result
}

/**
 * A description of the first structural fault in [script]'s speaker tags, or null when every turn
 * is a matched opener/closer pair of a known role.
 *
 * This is the check [RoleTagValidationAdvisor] was missing. Its existing checks ask whether a tag
 * names a valid role and whether any tag is present at all; neither can see tags that are
 * individually valid but wrongly ordered or wrongly paired, which is the fault that actually
 * scrambles an episode.
 *
 * Run on the script *after* the repairs above, so only what could not be repaired is reported and a
 * recoverable script never costs a second compose call.
 */
fun findTurnStructureProblem(script: String, roles: Set<String>): String? {
    var openRole: String? = null

    for (token in TAG_TOKEN_PATTERN.findAll(script)) {
        val role = token.groupValues[1]
        val isCloser = token.value.startsWith("</")

        if (isCloser) {
            val expected = openRole
                ?: return "the closing tag </$role> does not close any open turn"
            if (role != expected) {
                return "the <$expected> turn is closed with </$role>"
            }
            openRole = null
            continue
        }

        if (openRole != null) {
            return "the <$openRole> turn is never closed before <$role> opens"
        }
        if (role !in roles) {
            return "<$role> is not a valid speaker tag"
        }
        openRole = role
    }

    return openRole?.let { "the final <$it> turn is never closed" }
}

/**
 * Closes a final speaker turn the compose LLM opened but never closed, for the [roles] this podcast
 * actually uses.
 *
 * [SPEAKER_TURN_PATTERN] requires both tags, so an unclosed last turn matches nothing:
 * [stripOutsideSpeakerTags] then reads it as text sitting after the script and drops the whole
 * turn. Episode 202 lost its 517-character closing that way, on a turn the model had written
 * correctly and only failed to close.
 *
 * Deliberately narrow, like [normalizeSquareBracketSpeakerTags]. Only the tail after the last
 * complete turn is considered, it must begin with an opener of one of [roles], and its body must
 * contain no further speaker tag, so a genuinely malformed run of several turns is still left to be
 * discarded and logged rather than glued into one turn. Delivery markup inside the body, such as
 * `<break time="1s" />`, is not a speaker tag and does not block the recovery.
 *
 * A turn the model cut off mid-sentence is recovered as it stands: half a sentence spoken is a
 * smaller loss than a closing paragraph silently deleted, and the WARN says how much was recovered.
 */
fun closeUnterminatedFinalTurn(script: String, roles: Set<String>): String {
    val turns = SPEAKER_TURN_PATTERN.findAll(script).toList()
    val tailStart = if (turns.isEmpty()) 0 else turns.last().range.last + 1
    val tail = script.substring(tailStart).trim()
    if (tail.isEmpty()) return script

    val role = roles.firstOrNull { tail.startsWith("<$it>") } ?: return script
    val body = tail.removePrefix("<$role>")
    if (body.isBlank()) return script
    if (roles.any { body.contains("<$it>") || body.contains("</$it>") }) return script

    log.warn("Compose LLM left the final <{}> turn unclosed; recovered {} characters", role, body.trim().length)
    return script.substring(0, tailStart) + "\n<$role>" + body + "</$role>"
}

/**
 * The full clean-up a multi-speaker compose response goes through before it is stored, in the one
 * order that works: a square-bracketed opener is rewritten first so the turn becomes visible to
 * [SPEAKER_TURN_PATTERN], a mismatched closer is corrected next so the turns divide where the model
 * meant them to, an unclosed final turn is then closed so it is visible too, and only then is the
 * text outside the tags stripped, since that step discards whatever the earlier ones did not
 * recover.
 *
 * The closer repair must precede [closeUnterminatedFinalTurn], which locates the tail after the
 * last turn [SPEAKER_TURN_PATTERN] can see: run the other way round, a mismatched closer earlier in
 * the script hides every turn after it inside one swallowing match and the tail is computed from
 * the wrong place.
 *
 * Shared by [InterviewComposer] and [DialogueComposer], which differ only in how they arrive at
 * [roles].
 */
fun cleanUpComposedScript(script: String, roles: Set<String>): String =
    stripOutsideSpeakerTags(
        closeUnterminatedFinalTurn(
            repairMismatchedTurnClosers(
                normalizeSquareBracketSpeakerTags(script, roles), roles
            ), roles
        )
    )

/**
 * The set of speaker roles a compose-stage script is allowed to use, derived from the podcast's
 * configured TTS voices. Shared by prompt-building (so the model is told the valid tags) and
 * [RoleTagValidationAdvisor] (so a leaked tag outside this set is rejected before TTS ever sees it).
 */
fun resolveSpeakerRoles(podcast: Podcast): Set<String> =
    podcast.ttsVoices?.keys?.toSet() ?: setOf("host", "cohost")

/**
 * Strips any text before the first opening speaker tag and after the last closing speaker tag.
 * The compose LLM tends to "think out loud" before the script (e.g. "I have enough
 * context. Writing the script now.") before emitting the tagged script, despite the prompt
 * forbidding text outside speaker tags. The TTS parser already ignores such text, but it must
 * not be stored in the episode script (it shows in the dashboard and pollutes word counts).
 * Scripts without any speaker tags (briefing style) are returned unchanged.
 *
 * Discarding is logged at WARN when what goes is more than the expected scrap of meta-commentary,
 * because this function is otherwise silent about deleting spoken content. A discarded run that
 * contains a closing tag means a whole malformed turn was thrown away, which is how episode 184
 * lost its cold open without a single line in the log.
 */
fun stripOutsideSpeakerTags(script: String): String {
    val turns = SPEAKER_TURN_PATTERN.findAll(script).toList()
    if (turns.isEmpty()) return script
    warnIfSubstantial("before", script.substring(0, turns.first().range.first))
    warnIfSubstantial("after", script.substring(turns.last().range.last + 1))
    return script.substring(turns.first().range.first, turns.last().range.last + 1)
}

/** Length beyond which discarded text is too long to be the usual "Writing the script now." scrap. */
private const val EXPECTED_META_COMMENTARY_LENGTH = 200

private fun warnIfSubstantial(position: String, discarded: String) {
    val trimmed = discarded.trim()
    if (trimmed.isEmpty()) return
    if (trimmed.length <= EXPECTED_META_COMMENTARY_LENGTH && !trimmed.contains("</")) return
    log.warn(
        "Discarded {} characters of untagged text {} the script{}: '{}'",
        trimmed.length, position,
        if (trimmed.contains("</")) " (it contains a closing tag, so a malformed turn was dropped)" else "",
        trimmed.take(200)
    )
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

/**
 * Request options shared by every composer.
 *
 * Three things here are deliberate rather than incidental:
 *
 * `maxTokens` bounds the response, and matters beyond runaway protection: with no ceiling the
 * provider reserves the model's entire output window when checking affordability, which failed an
 * episode with a 402 demanding credit for 131,072 tokens to write a 1,876-word script.
 *
 * The reasoning effort is stated rather than left to the provider. Composition is the one stage
 * where reasoning earns its cost — it plans a long script — but OpenRouter infers an omitted
 * setting from the routed provider's defaults, and that produced compose output between 6,048 and
 * 72,821 tokens for scripts of comparable length. The effort is the run's (see [RunConfig]), which
 * [ResolvedModel.reasoningEffort] carries, and defaults to [resolveReasoningEffort].
 *
 * The routing floor keeps the request off lossy endpoints; see [OpenRouterRouting].
 */
fun buildComposeOptions(
    model: ResolvedModel,
    podcast: Podcast,
    appProperties: AppProperties
): OpenAiChatOptions.Builder {
    return OpenAiChatOptions.builder()
        .model(model.model)
        .temperature(resolveTemperature(podcast, appProperties))
        .maxTokens(appProperties.compose.maxOutputTokens)
        .withRoutingAndReasoning(model)
}

/** The run's configuration, or the podcast's own when the composer is called outside a run. */
internal fun ComposeContext.runConfigFor(podcast: Podcast, appProperties: AppProperties): RunConfig =
    runConfig ?: RunConfig.resolve(appProperties, podcast)
