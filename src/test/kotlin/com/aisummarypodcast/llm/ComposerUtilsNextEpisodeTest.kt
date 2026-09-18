package com.aisummarypodcast.llm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ComposerUtilsNextEpisodeTest {

    private val wednesday = LocalDate.of(2026, 9, 16)
    private val friday = LocalDate.of(2026, 9, 11)

    @Test
    fun `the day after the episode is phrased as tomorrow`() {
        val block = buildNextEpisodeBlock("en", wednesday, wednesday.plusDays(1))

        assertTrue(block.contains("The next episode is tomorrow (Thursday)."), block)
    }

    @Test
    fun `a gap of more than a day is phrased as the weekday`() {
        val block = buildNextEpisodeBlock("en", friday, LocalDate.of(2026, 9, 14))

        assertTrue(block.contains("The next episode is Monday."), block)
        assertTrue(!block.contains("tomorrow"), block)
    }

    @Test
    fun `the block forbids the invented cadence that caused the bug`() {
        val block = buildNextEpisodeBlock("en", wednesday, wednesday.plusDays(1))

        assertTrue(block.contains("\"next week\""), block)
    }

    @Test
    fun `the weekday is named in the podcast language`() {
        val block = buildNextEpisodeBlock("nl", friday, LocalDate.of(2026, 9, 14))

        assertTrue(block.contains("maandag"), block)
    }

    @Test
    fun `an unknown next episode leaves the prompt untouched`() {
        assertEquals("", buildNextEpisodeBlock("en", wednesday, null))
    }

    @Test
    fun `a next date that is not after the episode leaves the prompt untouched`() {
        assertEquals("", buildNextEpisodeBlock("en", wednesday, wednesday))
    }
}
