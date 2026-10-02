package com.aisummarypodcast.publishing

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OAuthCallbackMappersTest {

    @Test
    fun `connected answers 200 with the provider success message`() {
        val soundCloud = OAuthCallbackOutcome.Connected.toResponse(OAuthProviderPages.SOUNDCLOUD)
        val x = OAuthCallbackOutcome.Connected.toResponse(OAuthProviderPages.X)

        assertEquals(HttpStatus.OK, soundCloud.statusCode)
        assertEquals(MediaType.TEXT_HTML, soundCloud.headers.contentType)
        assertTrue(soundCloud.body!!.contains("SoundCloud connected successfully! You can close this tab."))
        assertTrue(x.body!!.contains("X account connected successfully! You can close this tab."))
    }

    @Test
    fun `exchange failure answers 200 with the provider failure message`() {
        val soundCloud = OAuthCallbackOutcome.ExchangeFailed.toResponse(OAuthProviderPages.SOUNDCLOUD)
        val x = OAuthCallbackOutcome.ExchangeFailed.toResponse(OAuthProviderPages.X)

        assertEquals(HttpStatus.OK, soundCloud.statusCode)
        assertTrue(soundCloud.body!!.contains("Failed to connect SoundCloud. Please try again."))
        assertEquals(HttpStatus.OK, x.statusCode)
        assertTrue(x.body!!.contains("Failed to connect X account. Please try again."))
    }

    @Test
    fun `provider refusal answers 200 with the escaped error`() {
        val response = OAuthCallbackOutcome.ProviderRefused("<script>alert(1)</script>")
            .toResponse(OAuthProviderPages.X)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.contains("X authorization failed: &lt;script&gt;alert(1)&lt;/script&gt;"))
        assertFalse(response.body!!.contains("<script>"))
    }

    @Test
    fun `invalid state answers 400`() {
        val response = OAuthCallbackOutcome.InvalidState.toResponse(OAuthProviderPages.X)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertTrue(response.body!!.contains("Authorization Error"))
        assertTrue(response.body!!.contains("Invalid or tampered authorization request."))
    }

    @Test
    fun `expired answers 400 for every provider`() {
        listOf(OAuthProviderPages.SOUNDCLOUD, OAuthProviderPages.X).forEach { pages ->
            val response = OAuthCallbackOutcome.Expired.toResponse(pages)

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertTrue(response.body!!.contains("Authorization Expired"))
            assertTrue(response.body!!.contains("The authorization request has expired. Please try again."))
        }
    }

    @Test
    fun `the page escapes title and message`() {
        val page = OAuthCallbackPage.render("<t>", "<m>")

        assertTrue(page.contains("&lt;t&gt;"))
        assertTrue(page.contains("&lt;m&gt;"))
        assertFalse(page.contains("<t>"))
        assertFalse(page.contains("<m>"))
    }
}
