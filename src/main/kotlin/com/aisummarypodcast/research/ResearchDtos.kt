package com.aisummarypodcast.research

data class ResearchTestResult(
    val success: Boolean,
    val message: String,
    val resultCount: Int? = null
)
