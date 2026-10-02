package com.aisummarypodcast.llm

import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.Podcast

/**
 * One tagged script composition: [kind] names the script ("dialogue", "interview") in log lines and
 * errors, [roles] are the speaker tags the script may use, and [prompt] is the style's finished prompt.
 */
data class TaggedComposeRequest(
    val kind: String,
    val roles: Set<String>,
    val articles: List<Article>,
    val podcast: Podcast,
    val model: ResolvedModel,
    val context: ComposeContext,
    val prompt: String
)
