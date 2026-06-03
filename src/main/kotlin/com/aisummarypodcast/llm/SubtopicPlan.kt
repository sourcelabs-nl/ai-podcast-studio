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
 * A kept rapid-fire item, carrying its source bucket so the prompt can display
 * both bucket and title in priority order.
 */
data class RapidFireItem(
    val article: Article,
    val bucketName: String,
    val bucketWeight: Int
)

/**
 * A composer-time plan that captures how a script should be segmented by subtopic when
 * the podcast has weighted subtopics configured. Subtopics with weight strictly greater
 * than [rapidFireWeightThreshold] form the full-segment tier; those at or below the
 * threshold (plus the synthetic "Other" bucket for unclassified articles) form the
 * rapid-fire tier.
 *
 * The rapid-fire tier is capped at [rapidFireMaxItems] and ranked by
 * (bucket weight desc, article relevance score desc, article id asc). Articles past the
 * cap are dropped from the script entirely.
 *
 * Returns null from [from] when subtopics are not configured, or when after grouping
 * the full-segment tier holds zero articles (in which case the composer falls back to
 * the flat layout per spec).
 */
data class SubtopicPlan(
    val fullSegments: List<SubtopicBucket>,
    val rapidFire: List<SubtopicBucket>,
    val rapidFireOrder: List<RapidFireItem>,
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
        fun from(
            podcast: Podcast,
            articles: List<Article>,
            targetWords: Int,
            rapidFireBudgetFraction: Double,
            rapidFireMaxItems: Int
        ): SubtopicPlan? {
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

            // Rank rapid-fire articles globally and cap. Drops past the cap are excluded
            // from the script entirely (per user direction: prefer fewer items with real
            // explanation over breadth).
            val rapidOrderAll = rapidRaw
                .flatMap { (name, arts) -> arts.map { RapidFireItem(it, name, weightOf(name)) } }
                .sortedWith(
                    compareByDescending<RapidFireItem> { it.bucketWeight }
                        .thenByDescending { it.article.relevanceScore ?: Int.MIN_VALUE }
                        .thenBy { it.article.id ?: Long.MAX_VALUE }
                )
            val rapidOrder = if (rapidFireMaxItems > 0) rapidOrderAll.take(rapidFireMaxItems) else emptyList()
            val keptByBucket = rapidOrder.groupBy { it.bucketName }

            val rapidFireBudget = (targetWords * rapidFireBudgetFraction).toInt().coerceAtLeast(0)
            val fullBudgetTotal = targetWords - rapidFireBudget
            val sumFullWeights = fullRaw.sumOf { (name, _) -> weightOf(name) }

            val fullSegments = fullRaw.map { (name, articles) ->
                val w = weightOf(name)
                val words = if (sumFullWeights == 0) 0 else (fullBudgetTotal.toLong() * w / sumFullWeights).toInt()
                SubtopicBucket(name = name, weight = w, articles = articles.toList(), wordBudget = words)
            }
            // Rebuild rapid-fire buckets to contain only kept articles, preserving the
            // original bucket iteration order so articleSubtopics stays consistent.
            val rapidFire = rapidRaw.mapNotNull { (name, _) ->
                val kept = keptByBucket[name]?.map { it.article } ?: return@mapNotNull null
                SubtopicBucket(name = name, weight = weightOf(name), articles = kept, wordBudget = 0)
            }

            return SubtopicPlan(
                fullSegments = fullSegments,
                rapidFire = rapidFire,
                rapidFireOrder = rapidOrder,
                rapidFireWordBudget = if (rapidFire.isEmpty()) 0 else rapidFireBudget,
                targetWords = targetWords,
                rapidFireWeightThreshold = threshold
            )
        }
    }
}

/**
 * Style-appropriate rapid-fire intro phrasing. The intro defines how the segment
 * announces itself (briefing header vs conversational handoff); the shared closing
 * instruction (built in [buildSubtopicPlanBlock]) enforces the per-item budget.
 */
enum class RapidFireStyle(val intro: String) {
    BRIEFING("Emit a clearly labeled rapid-fire segment titled \"And in brief:\" at the end of the script."),
    DIALOGUE("Before the sign-off, have the host introduce a rapid-fire segment with phrasing like \"Quick hits before we wrap\", alternating speakers naturally between items."),
    INTERVIEW("Before the sign-off, the interviewer should introduce a rapid-fire segment with phrasing like \"Lightning round to close\". The expert covers each item, with a brief interviewer reaction between items.")
}

fun buildSubtopicPlanBlock(plan: SubtopicPlan, style: RapidFireStyle): String {
    val fullLines = plan.fullSegments.joinToString("\n") { b ->
        "              - \"${b.name}\" (${b.articles.size} ${if (b.articles.size == 1) "article" else "articles"}, weight ${b.weight}): approximately ${b.wordBudget} words"
    }
    val rapidBlock = if (plan.hasRapidFire) {
        val count = plan.rapidFireOrder.size
        val wordsPerItem = if (count > 0) plan.rapidFireWordBudget / count else 0
        val itemLines = plan.rapidFireOrder.withIndex().joinToString("\n") { (idx, item) ->
            "              ${idx + 1}. [${item.bucketName}] \"${item.article.title}\""
        }
        """

            Rapid-fire tier (exactly $count item${if (count == 1) "" else "s"}, total approximately ${plan.rapidFireWordBudget} words, ~${wordsPerItem} words per item, cover in this order):
$itemLines

            ${style.intro} Cover each of the $count items above in roughly $wordsPerItem words. Do NOT merge multiple items into one sentence and do NOT skip any item. Do NOT skip the rapid-fire segment; it MUST appear after the full segments. Introduce the segment in plain, natural language (something like "a few quick ones to round things off") and flow from one item to the next conversationally: do NOT announce ordinal numbers or count the items aloud (no "item one, item two", no "first, second, third"). In a rapid-fire item, voice AT MOST ONE number, rounded to a clean value, and never read a benchmark name and its score together; lead with what the item means, not the metric."""
    } else ""

    return """


            Subtopic structure:
            Each article below is tagged with its subtopic in brackets (e.g. [subtopic: LLM releases]). Group your coverage by subtopic. Allocate script time per subtopic according to the budgets below, prioritizing the highest-weight subtopics.

            Full segments (cover each story in depth):
$fullLines$rapidBlock"""
}
