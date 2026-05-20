package com.aisummarypodcast.config

import com.aisummarypodcast.store.PodcastStyle
import com.aisummarypodcast.store.SourceType
import com.aisummarypodcast.store.Subtopics
import com.aisummarypodcast.store.TtsProviderType
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions
import org.springframework.data.jdbc.core.dialect.JdbcDialect
import org.springframework.data.relational.core.dialect.AnsiDialect

@Configuration
class SqliteDialectConfig {

    @Bean
    fun jdbcDialect(): JdbcDialect = object : JdbcDialect {
        private val delegate = AnsiDialect.INSTANCE

        override fun limit() = delegate.limit()
        override fun lock() = delegate.lock()
        override fun getSelectContext() = delegate.selectContext
    }

    @Bean
    fun jdbcCustomConversions(dialect: JdbcDialect): JdbcCustomConversions {
        return JdbcCustomConversions.of(
            dialect,
            listOf(
                IntegerToBooleanConverter(),
                BooleanToIntegerConverter(),
                StringToMapConverter(),
                MapToStringConverter(),
                StringToLlmModelOverridesConverter(),
                LlmModelOverridesToStringConverter(),
                StringToPodcastStyleConverter(),
                PodcastStyleToStringConverter(),
                StringToTtsProviderTypeConverter(),
                TtsProviderTypeToStringConverter(),
                StringToSourceTypeConverter(),
                SourceTypeToStringConverter(),
                StringToSubtopicsConverter(),
                SubtopicsToStringConverter()
            )
        )
    }

    @ReadingConverter
    class IntegerToBooleanConverter : Converter<Int, Boolean> {
        override fun convert(source: Int): Boolean = source != 0
    }

    @WritingConverter
    class BooleanToIntegerConverter : Converter<Boolean, Int> {
        override fun convert(source: Boolean): Int = if (source) 1 else 0
    }

    @ReadingConverter
    class StringToMapConverter : Converter<String, Map<String, String>> {
        private val objectMapper = jacksonObjectMapper()
        override fun convert(source: String): Map<String, String> = objectMapper.readValue(source)
    }

    @WritingConverter
    class MapToStringConverter : Converter<Map<String, String>, String> {
        private val objectMapper = jacksonObjectMapper()
        override fun convert(source: Map<String, String>): String = objectMapper.writeValueAsString(source)
    }

    @ReadingConverter
    class StringToLlmModelOverridesConverter : Converter<String, LlmModelOverrides> {
        private val objectMapper = jacksonObjectMapper()
        override fun convert(source: String): LlmModelOverrides = LlmModelOverrides(objectMapper.readValue(source))
    }

    @WritingConverter
    class LlmModelOverridesToStringConverter : Converter<LlmModelOverrides, String> {
        private val objectMapper = jacksonObjectMapper()
        override fun convert(source: LlmModelOverrides): String = objectMapper.writeValueAsString(source.stages)
    }

    // Enum converters below are required because these enums use custom `value` fields
    // (e.g., "news-briefing") that differ from the enum name. Spring Data JDBC's built-in
    // valueOf() would fail, so we use fromValue() for correct round-tripping.

    @ReadingConverter
    class StringToPodcastStyleConverter : Converter<String, PodcastStyle> {
        override fun convert(source: String): PodcastStyle =
            PodcastStyle.fromValue(source) ?: throw IllegalArgumentException("Unknown podcast style: $source")
    }

    @WritingConverter
    class PodcastStyleToStringConverter : Converter<PodcastStyle, String> {
        override fun convert(source: PodcastStyle): String = source.value
    }

    @ReadingConverter
    class StringToTtsProviderTypeConverter : Converter<String, TtsProviderType> {
        override fun convert(source: String): TtsProviderType =
            TtsProviderType.fromValue(source) ?: throw IllegalArgumentException("Unknown TTS provider: $source")
    }

    @WritingConverter
    class TtsProviderTypeToStringConverter : Converter<TtsProviderType, String> {
        override fun convert(source: TtsProviderType): String = source.value
    }

    @ReadingConverter
    class StringToSourceTypeConverter : Converter<String, SourceType> {
        override fun convert(source: String): SourceType =
            SourceType.fromValue(source) ?: throw IllegalArgumentException("Unknown source type: $source")
    }

    @WritingConverter
    class SourceTypeToStringConverter : Converter<SourceType, String> {
        override fun convert(source: SourceType): String = source.value
    }

    @ReadingConverter
    class StringToSubtopicsConverter : Converter<String, Subtopics> {
        private val objectMapper = jacksonObjectMapper()
        override fun convert(source: String): Subtopics = Subtopics(objectMapper.readValue(source))
    }

    @WritingConverter
    class SubtopicsToStringConverter : Converter<Subtopics, String> {
        private val objectMapper = jacksonObjectMapper()
        override fun convert(source: Subtopics): String = objectMapper.writeValueAsString(source.weights)
    }
}
