package com.aisummarypodcast.llm

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ComposerUtilsDaysAndTimesTest {

    @Test
    fun `yesterday is the calendar day before, also across a weekend`() {
        val monday = LocalDate.of(2026, 10, 19)

        val block = buildDaysAndTimesBlock(monday)

        assertTrue(block.contains("Today is Monday and yesterday was Sunday."), block)
    }

    @Test
    fun `weekdays are named in English whatever the podcast language`() {
        val block = buildDaysAndTimesBlock(LocalDate.of(2026, 10, 22))

        assertTrue(block.contains("Today is Thursday and yesterday was Wednesday."), block)
    }
}
