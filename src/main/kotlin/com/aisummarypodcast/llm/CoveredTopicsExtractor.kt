package com.aisummarypodcast.llm

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

data class CoveredTopicsExtractionResult(
    val recap: String,
    val coveredTopics: List<String>
)

/**
 * Parses the recap response: the recap text followed by an optional delimited JSON array of the
 * topic labels actually discussed in the episode script. Mirrors [TopicOrderExtractor]. When the
 * block is absent or unparseable, returns the full response as the recap and an empty topic list,
 * so callers degrade gracefully (and never prune topics on a parse miss).
 */
@Component
class CoveredTopicsExtractor(
    private val jsonMapper: JsonMapper
) {

    fun extract(rawResponse: String): CoveredTopicsExtractionResult {
        val startIndex = rawResponse.indexOf(START_DELIMITER)
        if (startIndex == -1) {
            return CoveredTopicsExtractionResult(rawResponse.trim(), emptyList())
        }

        val endIndex = rawResponse.indexOf(END_DELIMITER, startIndex)
        if (endIndex == -1) {
            return CoveredTopicsExtractionResult(rawResponse.substring(0, startIndex).trim(), emptyList())
        }

        val jsonContent = rawResponse.substring(startIndex + START_DELIMITER.length, endIndex).trim()
        val recap = rawResponse.substring(0, startIndex).trim()

        return try {
            val topics: List<String> = jsonMapper.readValue(
                jsonContent,
                jsonMapper.typeFactory.constructCollectionType(List::class.java, String::class.java)
            )
            CoveredTopicsExtractionResult(recap, topics)
        } catch (e: Exception) {
            log.warn("Failed to parse covered topics JSON: {}", e.message)
            CoveredTopicsExtractionResult(recap, emptyList())
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(CoveredTopicsExtractor::class.java)
        private const val START_DELIMITER = "|||COVERED_TOPICS|||"
        private const val END_DELIMITER = "|||END_COVERED_TOPICS|||"
    }
}
