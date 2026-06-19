package com.aisummarypodcast.publishing

import com.aisummarypodcast.store.PodcastPublicationTarget
import com.aisummarypodcast.store.PodcastPublicationTargetRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PodcastPublicationTargetServiceTest {

    private val repository = mockk<PodcastPublicationTargetRepository>(relaxed = true)
    private val service = PodcastPublicationTargetService(repository)

    @Test
    fun `list returns all targets for podcast`() {
        val targets = listOf(
            PodcastPublicationTarget(id = 1, podcastId = "pod1", target = "soundcloud", config = "{}", enabled = true),
            PodcastPublicationTarget(id = 2, podcastId = "pod1", target = "ftp", config = "{}", enabled = false)
        )
        every { repository.findByPodcastId("pod1") } returns targets

        val result = service.list("pod1")

        assertEquals(2, result.size)
    }

    @Test
    fun `list returns empty for podcast with no targets`() {
        every { repository.findByPodcastId("pod1") } returns emptyList()

        val result = service.list("pod1")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `get returns target when exists`() {
        val target = PodcastPublicationTarget(id = 1, podcastId = "pod1", target = "ftp", config = "{}", enabled = true)
        every { repository.findByPodcastIdAndTarget("pod1", "ftp") } returns target

        val result = service.get("pod1", "ftp")

        assertNotNull(result)
        assertEquals("ftp", result!!.target)
    }

    @Test
    fun `get returns null when not exists`() {
        every { repository.findByPodcastIdAndTarget("pod1", "ftp") } returns null

        val result = service.get("pod1", "ftp")

        assertNull(result)
    }

    @Test
    fun `upsert creates new target`() {
        every { repository.upsert(any()) } answers {
            firstArg<PodcastPublicationTarget>().copy(id = 1)
        }

        val result = service.upsert("pod1", "ftp", """{"remotePath":"/shows/"}""", true, false)

        assertEquals("ftp", result.target)
        assertTrue(result.enabled)
    }

    @Test
    fun `upsert updates existing target`() {
        every { repository.upsert(any()) } answers {
            firstArg<PodcastPublicationTarget>().copy(id = 1)
        }

        val result = service.upsert("pod1", "ftp", """{"remotePath":"/new/"}""", true, false)

        assertEquals(1L, result.id)
        assertTrue(result.enabled)
    }

    @Test
    fun `delete returns true when target exists`() {
        every { repository.deleteByPodcastIdAndTarget("pod1", "ftp") } returns 1L

        assertTrue(service.delete("pod1", "ftp"))
    }

    @Test
    fun `delete returns false when target does not exist`() {
        every { repository.deleteByPodcastIdAndTarget("pod1", "ftp") } returns 0L

        assertFalse(service.delete("pod1", "ftp"))
    }
}
