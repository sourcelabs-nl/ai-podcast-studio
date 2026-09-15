package com.aisummarypodcast.llm

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

@RestController
@RequestMapping("/llm/calls")
class LlmCallLatencyController(private val llmCallLatencyService: LlmCallLatencyService) {

    /** [days] bounds how far back the percentiles are computed; it cannot exceed the retention window. */
    @GetMapping("/latency")
    fun latency(@RequestParam(defaultValue = "7") days: Long): ResponseEntity<LlmCallLatencyResponse> {
        if (days < 1) return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(llmCallLatencyService.latencySince(Duration.ofDays(days)))
    }
}
