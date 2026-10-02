package com.aisummarypodcast.research

import com.aisummarypodcast.user.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users/{userId}/research/test")
class ResearchTestController(
    private val userService: UserService,
    private val researchService: ResearchService
) {

    @PostMapping("/tavily")
    fun testTavily(@PathVariable userId: String): ResponseEntity<ResearchTestResult> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(researchService.testTavily(userId))
    }
}
