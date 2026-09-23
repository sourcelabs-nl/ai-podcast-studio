package com.aisummarypodcast.eval

import java.security.MessageDigest

/** Hex SHA-256 of a script, identifying the exact text a stored score was judged against. */
fun scriptHashOf(scriptText: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(scriptText.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
