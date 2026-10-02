package com.aisummarypodcast.publishing

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.EncryptionProperties
import com.aisummarypodcast.config.OAuthPkceHelper
import io.mockk.every
import io.mockk.mockk
import java.net.URI
import java.net.URLDecoder
import java.util.Base64

/** A real [OAuthPkceHelper] signing with a fixed key, so states round-trip through the flow. */
fun testPkceHelper(appProperties: AppProperties = mockk()): OAuthPkceHelper {
    every { appProperties.encryption } returns
        EncryptionProperties(masterKey = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() }))
    return OAuthPkceHelper(appProperties)
}

fun queryParam(url: String, name: String): String? =
    URI(url).rawQuery.split("&")
        .map { it.split("=", limit = 2) }
        .firstOrNull { it[0] == name }
        ?.let { URLDecoder.decode(it[1], Charsets.UTF_8) }
