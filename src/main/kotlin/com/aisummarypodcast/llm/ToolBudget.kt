package com.aisummarypodcast.llm

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Per-compose-call cap on tool invocations. Each tool is keyed by name and has its own counter.
 * Once the cap is hit, [tryConsume] returns false so callers can short-circuit with a
 * structured "budget exhausted" response instead of throwing.
 *
 * Instances are intended to be built fresh for every compose call; the underlying counters
 * are thread-safe so concurrent tool invocations within a single compose loop are accounted
 * for correctly.
 */
class ToolBudget {

    private val counters = ConcurrentHashMap<String, AtomicInteger>()
    private val caps = ConcurrentHashMap<String, Int>()

    fun register(toolName: String, cap: Int): ToolBudget {
        require(cap >= 0) { "cap must be non-negative" }
        caps[toolName] = cap
        counters.computeIfAbsent(toolName) { AtomicInteger(0) }
        return this
    }

    fun tryConsume(toolName: String): Boolean {
        val cap = caps[toolName] ?: return true
        val counter = counters.computeIfAbsent(toolName) { AtomicInteger(0) }
        while (true) {
            val current = counter.get()
            if (current >= cap) return false
            if (counter.compareAndSet(current, current + 1)) return true
        }
    }

    fun invocations(toolName: String): Int = counters[toolName]?.get() ?: 0
}
