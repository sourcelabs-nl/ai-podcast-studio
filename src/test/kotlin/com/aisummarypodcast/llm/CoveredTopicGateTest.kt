package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.BriefingProperties
import com.aisummarypodcast.config.ComposeProperties
import com.aisummarypodcast.config.DedupGateProperties
import com.aisummarypodcast.config.DedupProperties
import com.aisummarypodcast.config.EncryptionProperties
import com.aisummarypodcast.config.EpisodesProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.LlmProperties
import com.aisummarypodcast.store.Article
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val COVERED = listOf("Opus 5 release", "Mistral Series C")

class CoveredTopicGateTest {

    private val jevClient = mockk<JevClient>()

    private fun gate(properties: DedupGateProperties = DedupGateProperties()) = CoveredTopicGate(
        jevClient,
        AppProperties(
            llm = LlmProperties(dedup = DedupProperties(gate = properties)),
            briefing = BriefingProperties(),
            episodes = EpisodesProperties(),
            feed = FeedProperties(),
            encryption = EncryptionProperties(masterKey = "test"),
            compose = ComposeProperties(maxArticles = 40)
        )
    )

    private fun article(id: Long, title: String = "Article $id", summary: String? = "Summary $id") = Article(
        id = id,
        sourceId = "src-1",
        title = title,
        body = "Body of $id",
        url = "https://example.com/$id",
        contentHash = "hash-$id",
        summary = summary
    )

    private fun answering(vararg values: Pair<String, Double>, cost: Double? = 0.0007) {
        every { jevClient.ask(any(), any(), any(), any(), any()) } returns
            JevAnswers(values.toMap(), inputTokens = 100, reportedCostUsd = cost)
    }

    @Test
    fun `excludes a candidate at or above the threshold and keeps one below it`() {
        answering("a1" to 0.95, "a2" to 0.8, "a3" to 0.2)

        val result = gate().evaluate(listOf(article(1), article(2), article(3)), COVERED, "u1")

        assertEquals(setOf(1L, 2L), result.excludedIds)
        assertTrue(result.answered)
        assertEquals(0.0007, result.reportedCostUsd)
    }

    @Test
    fun `an unanswered candidate is treated as not covered`() {
        answering("a1" to 0.99)

        val result = gate().evaluate(listOf(article(1), article(2)), COVERED, "u1")

        assertEquals(setOf(1L), result.excludedIds)
        assertTrue(result.answered)
    }

    @Test
    fun `an empty answer set leaves every candidate and reports no cost`() {
        every { jevClient.ask(any(), any(), any(), any(), any()) } returns JevAnswers.NONE

        val result = gate().evaluate(listOf(article(1), article(2)), COVERED, "u1")

        assertTrue(result.excludedIds.isEmpty())
        assertFalse(result.answered)
        assertNull(result.reportedCostUsd)
    }

    @Test
    fun `no covered topics means no request`() {
        val result = gate().evaluate(listOf(article(1)), emptyList(), "u1")

        assertFalse(result.answered)
        verify(exactly = 0) { jevClient.ask(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a disabled gate makes no request`() {
        val result = gate(DedupGateProperties(enabled = false)).evaluate(listOf(article(1)), COVERED, "u1")

        assertFalse(result.answered)
        verify(exactly = 0) { jevClient.ask(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `the state carries the covered topics and truncated candidate summaries`() {
        val state = slot<Any>()
        every { jevClient.ask(any(), capture(state), any(), any(), any()) } returns JevAnswers(mapOf("a1" to 0.1))

        gate(DedupGateProperties(summaryMaxChars = 10))
            .evaluate(listOf(article(1, summary = "A very long summary indeed")), COVERED, "u1")

        @Suppress("UNCHECKED_CAST")
        val captured = state.captured as Map<String, Any>
        assertEquals(COVERED, captured["covered_topics"])

        @Suppress("UNCHECKED_CAST")
        val candidates = captured["candidates"] as Map<String, Map<String, String>>
        assertEquals("A very lon", candidates.getValue("1").getValue("summary"))
        assertEquals("Article 1", candidates.getValue("1").getValue("title"))
    }

    @Test
    fun `a candidate with no summary falls back to its body`() {
        val state = slot<Any>()
        every { jevClient.ask(any(), capture(state), any(), any(), any()) } returns JevAnswers(mapOf("a1" to 0.1))

        gate().evaluate(listOf(article(1, summary = null)), COVERED, "u1")

        @Suppress("UNCHECKED_CAST")
        val candidates = (state.captured as Map<String, Any>)["candidates"] as Map<String, Map<String, String>>
        assertEquals("Body of 1", candidates.getValue("1").getValue("summary"))
    }

    @Test
    fun `candidates are split into chunks that fit the character budget`() {
        // Each candidate is charged its title plus the summary cap plus a fixed overhead, so a
        // budget of three candidates' worth needs three requests for seven candidates.
        val perCandidate = "Article 1".length + 300 + 400
        val properties = DedupGateProperties(maxRequestChars = perCandidate * 3 + COVERED.sumOf { it.length + 4 })

        val chunks = gate(properties).chunkCandidates((1L..7L).map { article(it) }, COVERED)

        assertEquals(7, chunks.sumOf { it.size })
        assertTrue(chunks.all { it.size <= 3 })
    }

    @Test
    fun `chunks are evenly sized rather than leaving a tiny tail`() {
        // A real run of 186 candidates against 190 covered topics split 184 and 2 under a greedy
        // fill, and the second request carried the whole topic list to ask about two articles.
        val perCandidate = "Article 1".length + 300 + 400
        val properties = DedupGateProperties(maxRequestChars = perCandidate * 100 + COVERED.sumOf { it.length + 4 })

        val chunks = gate(properties).chunkCandidates((1L..186L).map { article(it) }, COVERED)

        assertEquals(186, chunks.sumOf { it.size })
        assertTrue(chunks.all { it.size <= 100 }, "every chunk fits the budget")
        assertTrue(chunks.minOf { it.size } >= chunks.maxOf { it.size } - 1, "chunks are within one of each other")
    }

    @Test
    fun `a single chunk is used when everything fits`() {
        val chunks = gate().chunkCandidates((1L..50L).map { article(it) }, COVERED)

        assertEquals(1, chunks.size)
    }

    @Test
    fun `answers and costs from several chunks are merged`() {
        val properties = DedupGateProperties(maxRequestChars = ("Article 1".length + 300 + 400) + COVERED.sumOf { it.length + 4 })
        every { jevClient.ask(any(), any(), any(), any(), any()) } returnsMany listOf(
            JevAnswers(mapOf("a1" to 0.95), reportedCostUsd = 0.0002),
            JevAnswers(mapOf("a2" to 0.10), reportedCostUsd = 0.0003)
        )

        val result = gate(properties).evaluate(listOf(article(1), article(2)), COVERED, "u1")

        assertEquals(setOf(1L), result.excludedIds)
        assertEquals(0.0005, result.reportedCostUsd!!, 1e-9)
        verify(exactly = 2) { jevClient.ask(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `one failing chunk leaves its candidates unexcluded without losing the other`() {
        val properties = DedupGateProperties(maxRequestChars = ("Article 1".length + 300 + 400) + COVERED.sumOf { it.length + 4 })
        every { jevClient.ask(any(), any(), any(), any(), any()) } returnsMany listOf(
            JevAnswers.NONE,
            JevAnswers(mapOf("a2" to 0.99), reportedCostUsd = 0.0003)
        )

        val result = gate(properties).evaluate(listOf(article(1), article(2)), COVERED, "u1")

        assertEquals(setOf(2L), result.excludedIds)
        assertTrue(result.answered)
    }

    @Test
    fun `covered topics larger than the whole budget ask nothing`() {
        val chunks = gate(DedupGateProperties(maxRequestChars = 5)).chunkCandidates(listOf(article(1)), COVERED)

        assertTrue(chunks.isEmpty())
    }

    @Test
    fun `the call names the episode the gate ran for`() {
        val caller = slot<JevCaller>()
        every { jevClient.ask(any(), any(), any(), any(), capture(caller)) } returns
            JevAnswers(mapOf("a1" to 0.1))

        gate().evaluate(listOf(article(1)), COVERED, "u1", episodeId = 214L)

        assertEquals(214L, caller.captured.episodeId)
        assertEquals(DEDUP_GATE_STAGE, caller.captured.stage)
    }

    @Test
    fun `a gate run outside an episode names none`() {
        val caller = slot<JevCaller>()
        every { jevClient.ask(any(), any(), any(), any(), capture(caller)) } returns
            JevAnswers(mapOf("a1" to 0.1))

        // The preview path deduplicates before any episode exists.
        gate().evaluate(listOf(article(1)), COVERED, "u1")

        assertNull(caller.captured.episodeId)
    }
}
