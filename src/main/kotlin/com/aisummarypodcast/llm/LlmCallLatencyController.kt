package com.aisummarypodcast.llm

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

@RestController
@RequestMapping("/llm/calls")
class LlmCallLatencyController(private val llmCallLatencyService: LlmCallLatencyService) {

    /**
     * Percentiles either across all episodes over a window, or for one episode.
     *
     * [days] bounds how far back the percentiles are computed and cannot exceed the retention
     * window. It is ignored when [episodeId] is given: an episode is a bounded set of requests, so
     * intersecting it with a window could only hide some of them.
     */
    @GetMapping("/latency")
    fun latency(
        @RequestParam(defaultValue = "7") days: Long,
        @RequestParam(required = false) episodeId: Long?
    ): ResponseEntity<LlmCallLatencyResponse> {
        if (episodeId != null) return ResponseEntity.ok(llmCallLatencyService.latencyForEpisode(episodeId))
        if (days < 1) return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(llmCallLatencyService.latencySince(Duration.ofDays(days)))
    }

    /** The individual requests one episode issued, including cached and failed ones. */
    @GetMapping("/episodes/{episodeId}")
    fun episodeCalls(@PathVariable episodeId: Long): ResponseEntity<EpisodeLlmCallsResponse> =
        ResponseEntity.ok(llmCallLatencyService.requestsForEpisode(episodeId))
}
