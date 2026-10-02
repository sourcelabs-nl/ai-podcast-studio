package com.aisummarypodcast.podcast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SafeSseEmitterTest {

    /** Counts the writes in flight at once, which the real emitter does not survive being more than one. */
    private class OverlapDetectingEmitter : SseEmitter() {
        private val inFlight = AtomicInteger()
        val maxInFlight = AtomicInteger()
        val writes = AtomicInteger()

        override fun send(builder: SseEventBuilder) = write()
        override fun complete() = write()

        private fun write() {
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
            Thread.sleep(2)
            writes.incrementAndGet()
            inFlight.decrementAndGet()
        }
    }

    @Test
    fun `concurrent sends and a completion never interleave`() {
        val raw = OverlapDetectingEmitter()
        val emitter = SafeSseEmitter(raw)
        val threads = 8
        val sendsPerThread = 10
        val pool = Executors.newFixedThreadPool(threads + 1)
        val start = CountDownLatch(1)

        repeat(threads) {
            pool.execute {
                start.await()
                repeat(sendsPerThread) { emitter.send("progress", "{}") }
            }
        }
        pool.execute {
            start.await()
            emitter.complete()
        }
        start.countDown()
        pool.shutdown()
        pool.awaitTermination(30, TimeUnit.SECONDS)

        assertEquals(1, raw.maxInFlight.get())
        assertEquals(threads * sendsPerThread + 1, raw.writes.get())
    }
}
