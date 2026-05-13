package com.aisummarypodcast.llm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ToolBudgetTest {

    @Test
    fun `consumes up to cap then short-circuits`() {
        val budget = ToolBudget().register("t", 3)

        assertTrue(budget.tryConsume("t"))
        assertTrue(budget.tryConsume("t"))
        assertTrue(budget.tryConsume("t"))
        assertFalse(budget.tryConsume("t"))
        assertEquals(3, budget.invocations("t"))
    }

    @Test
    fun `unregistered tool is unbounded`() {
        val budget = ToolBudget()
        repeat(50) { assertTrue(budget.tryConsume("free")) }
    }

    @Test
    fun `cap of zero rejects immediately`() {
        val budget = ToolBudget().register("t", 0)
        assertFalse(budget.tryConsume("t"))
    }

    @Test
    fun `concurrent compose calls get independent budgets`() {
        val executor = Executors.newFixedThreadPool(4)
        val composeCount = 50
        val cap = 5
        val overConsumptions = AtomicInteger(0)

        val futures = (1..composeCount).map {
            executor.submit {
                val budget = ToolBudget().register("t", cap)
                var consumed = 0
                repeat(cap * 2) { if (budget.tryConsume("t")) consumed++ }
                if (consumed > cap) overConsumptions.incrementAndGet()
            }
        }
        futures.forEach { it.get() }
        executor.shutdown()

        assertEquals(0, overConsumptions.get())
    }

    @Test
    fun `concurrent tryConsume within a single budget never exceeds cap`() {
        val budget = ToolBudget().register("t", 100)
        val executor = Executors.newFixedThreadPool(8)
        val successes = AtomicInteger(0)

        val futures = (1..1000).map {
            executor.submit { if (budget.tryConsume("t")) successes.incrementAndGet() }
        }
        futures.forEach { it.get() }
        executor.shutdown()

        assertEquals(100, successes.get())
        assertEquals(100, budget.invocations("t"))
    }
}
