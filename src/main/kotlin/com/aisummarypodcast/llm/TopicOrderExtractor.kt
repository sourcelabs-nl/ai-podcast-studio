package com.aisummarypodcast.llm

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

data class TopicOrderExtractionResult(
    val script: String,
    val topicOrder: List<String>
)

@Component
class TopicOrderExtractor(
    private val jsonMapper: JsonMapper
) {

    fun extract(rawResponse: String): TopicOrderExtractionResult {
        val startIndex = rawResponse.indexOf(START_DELIMITER)
        if (startIndex == -1) {
            return TopicOrderExtractionResult(rawResponse, emptyList())
        }

        val endIndex = rawResponse.indexOf(END_DELIMITER, startIndex)
        if (endIndex == -1) {
            return TopicOrderExtractionResult(rawResponse, emptyList())
        }

        val jsonContent = rawResponse.substring(startIndex + START_DELIMITER.length, endIndex).trim()
        val script = rawResponse.substring(0, startIndex).trimEnd()

        return try {
            val topicOrder: List<String> = jsonMapper.readValue(jsonContent, jsonMapper.typeFactory.constructCollectionType(List::class.java, String::class.java))
            TopicOrderExtractionResult(script, topicOrder)
        } catch (e: Exception) {
            log.warn("Failed to parse topic order JSON: {}", e.message)
            TopicOrderExtractionResult(script, emptyList())
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(TopicOrderExtractor::class.java)
        private const val START_DELIMITER = "|||TOPIC_ORDER|||"
        private const val END_DELIMITER = "|||END_TOPIC_ORDER|||"
    }
}
