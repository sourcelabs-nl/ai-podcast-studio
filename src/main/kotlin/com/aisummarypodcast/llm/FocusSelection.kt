package com.aisummarypodcast.llm

/**
 * The articles a focus episode composes from, scored against its focus text, with what scoring them
 * cost. [articles] carry the focus relevance and summary in memory only (see [FocusScoredArticle]).
 */
data class FocusSelection(
    val articles: List<FilteredArticle>,
    val filterModel: String,
    val scoreInputTokens: Int,
    val scoreOutputTokens: Int,
    val scoreCostCents: Int,
    val scoreCostSource: LlmCostSource,
    val scoreReportedCostCents: Double?
)

/**
 * No candidate article cleared the relevance threshold against a focus episode's focus text. The
 * run fails rather than composing an empty or off-topic script.
 */
class NoFocusRelevantArticlesException(focus: String) :
    IllegalStateException("No relevant articles found for focus \"$focus\" in the current article window")
