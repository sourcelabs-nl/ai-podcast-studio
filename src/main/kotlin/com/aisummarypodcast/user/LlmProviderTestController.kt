package com.aisummarypodcast.user

import com.aisummarypodcast.publishing.TestConnectionResult
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/users/{userId}/api-keys/LLM/{provider}/test")
class LlmProviderTestController(
    private val userService: UserService,
    private val llmProviderTestService: LlmProviderTestService
) {

    @PostMapping
    fun test(@PathVariable userId: String, @PathVariable provider: String): ResponseEntity<TestConnectionResult> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(llmProviderTestService.test(userId, provider))
    }
}
