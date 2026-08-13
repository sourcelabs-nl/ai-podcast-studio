package com.aisummarypodcast.publishing

import org.apache.commons.net.ftp.FTPClient

/**
 * Closes the connection without letting a disconnect problem mask the outcome of the work that was
 * being done. Used from `finally` blocks, where an exception would replace the real failure.
 */
internal fun FTPClient.disconnectQuietly() {
    try {
        if (isConnected) disconnect()
    } catch (_: Exception) {
        // Nothing useful to do: the connection is being abandoned either way.
    }
}
