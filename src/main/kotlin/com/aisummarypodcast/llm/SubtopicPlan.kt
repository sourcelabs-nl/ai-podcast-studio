package com.aisummarypodcast.llm

import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.Podcast

const val OTHER_BUCKET = "Other"
private const val OTHER_BUCKET_WEIGHT = 1

data class SubtopicBucket(
    val name: String,
    val weight: Int,
    val articles: List<Article>,
    val wordBudget: Int
)

/**
 * A composer-time plan that captures how a script should be segmented by subtopic when
 * the podcast has weighted subtopics configured. Subtopics with weight strictly greater
 * than [rapidFireWeightThreshold] form the full-segment tier; those at or below the
 * threshold (plus the synthetic "Other" bucket for unclassified articles) form the
 * rapid-fire tier.
 *
 * Returns null from [from] when subtopics are not configured, or when after grouping
 * the full-segment tier holds zero articles (in which case the composer falls back to
 * the flat layout per spec).
 */
data class SubtopicPlan(
    val fullSegments: List<SubtopicBucket>,
    val rapidFire: List<SubtopicBucket>,
    val rapidFireWordBudget: Int,
    val targetWords: Int,
    val rapidFireWeightThreshold: Int
) {
    val articleSubtopics: Map<Long, String> by lazy {
        buildMap {
            (fullSegments + rapidFire).forEach { bucket ->
                bucket.articles.forEach { article ->
                    article.id?.let { put(it, bucket.name) }
                }
            }
        }
    }

    val hasRapidFire: Boolean get() = rapidFire.isNotEmpty()

    companion object {
        fun from(podcast: Podcast, articles: List<Article>, targetWords: Int, rapidFireBudgetFraction: Double): SubtopicPlan? {
            val configured = podcast.subtopics?.weights ?: return null
            if (configured.isEmpty()) return null

            val threshold = podcast.rapidFireWeightThreshold

            val byBucket = LinkedHashMap<String, MutableList<Article>>()
            configured.keys.forEach { byBucket[it] = mutableListOf() }
            byBucket[OTHER_BUCKET] = mutableListOf()

            for (article in articles) {
                val key = article.subtopic?.let { sub -> configured.keys.firstOrNull { it.equals(sub, ignoreCase = true) } }
                    ?: OTHER_BUCKET
                byBucket.getValue(key).add(article)
            }

            val weightOf: (String) -> Int = { name ->
                if (name == OTHER_BUCKET) OTHER_BUCKET_WEIGHT else configured[name] ?: OTHER_BUCKET_WEIGHT
            }

            val (fullRaw, rapidRaw) = byBucket
                .filterValues { it.isNotEmpty() }
                .entries
                .partition { (name, _) -> weightOf(name) > threshold }

            if (fullRaw.isEmpty()) return null

            val rapidFireBudget = (targetWords * rapidFireBudgetFraction).toInt().coerceAtLeast(0)
            val fullBudgetTotal = targetWords - rapidFireBudget
            val sumFullWeights = fullRaw.sumOf { (name, _) -> weightOf(name) }

            val fullSegments = fullRaw.map { (name, articles) ->
                val w = weightOf(name)
                val words = if (sumFullWeights == 0) 0 else (fullBudgetTotal.toLong() * w / sumFullWeights).toInt()
                SubtopicBucket(name = name, weight = w, articles = articles.toList(), wordBudget = words)
            }
            val rapidFire = rapidRaw.map { (name, articles) ->
                SubtopicBucket(name = name, weight = weightOf(name), articles = articles.toList(), wordBudget = 0)
            }

            return SubtopicPlan(
                fullSegments = fullSegments,
                rapidFire = rapidFire,
                rapidFireWordBudget = if (rapidRaw.isEmpty()) 0 else rapidFireBudget,
                targetWords = targetWords,
                rapidFireWeightThreshold = threshold
            )
        }
    }
}

/**
 * Style-appropriate rapid-fire phrasing instruction. The label is what the LLM should use
 * to introduce the rapid-fire segment; the style determines whether it reads as a header
 * (briefing) or conversational handoff (dialogue/interview).
 */
enum class RapidFireStyle(val label: String, val instruction: String) {
    BRIEFING("And in brief:", "Emit a clearly labeled rapid-fire segment titled \"And in brief:\" at the end of the script. Cover each rapid-fire article in one to two sentences."),
    DIALOGUE("Quick hits before we wrap", "Before the sign-off, have the host introduce a rapid-fire segment with phrasing like \"Quick hits before we wrap\". Cover each rapid-fire article in one to two sentences, alternating speakers naturally."),
    INTERVIEW("Lightning round to close", "Before the sign-off, the interviewer should introduce a rapid-fire segment with phrasing like \"Lightning round to close\". The expert covers each rapid-fire article in one to two sentences, with brief interviewer reactions between them.")
}

fun buildSubtopicPlanBlock(plan: SubtopicPlan, style: RapidFireStyle): String {
    val fullLines = plan.fullSegments.joinToString("\n") { b ->
        "              - \"${b.name}\" (${b.articles.size} ${if (b.articles.size == 1) "article" else "articles"}, weight ${b.weight}): approximately ${b.wordBudget} words"
    }
    val rapidBlock = if (plan.hasRapidFire) {
        val rapidLines = plan.rapidFire.joinToString("\n") { b ->
            "              - \"${b.name}\" (${b.articles.size} ${if (b.articles.size == 1) "article" else "articles"})"
        }
        """

            Rapid-fire tier (total approximately ${plan.rapidFireWordBudget} words):
$rapidLines

            ${style.instruction} Do NOT skip the rapid-fire segment; it MUST appear after the full segments."""
    } else ""

    return """


            Subtopic structure:
            Each article below is tagged with its subtopic in brackets (e.g. [subtopic: LLM releases]). Group your coverage by subtopic. Allocate script time per subtopic according to the budgets below, prioritizing the highest-weight subtopics.

            Full segments (cover each story in depth):
$fullLines$rapidBlock"""
}
