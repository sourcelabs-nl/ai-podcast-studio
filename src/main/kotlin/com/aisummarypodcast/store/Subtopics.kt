package com.aisummarypodcast.store

data class Subtopics(
    val weights: Map<String, Int> = emptyMap()
) {
    fun isEmpty(): Boolean = weights.isEmpty()
    fun isNotEmpty(): Boolean = weights.isNotEmpty()
    operator fun get(key: String): Int? = weights[key]
}
