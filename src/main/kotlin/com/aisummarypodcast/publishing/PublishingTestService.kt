package com.aisummarypodcast.publishing

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PublishingTestService(
    private val soundCloudTokenManager: SoundCloudTokenManager,
    private val soundCloudClient: SoundCloudClient,
    private val ftpConnectionFactory: FtpConnectionFactory
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Runs the exact connection publishing would make, including the fallback to the other transfer
     * mode, so a passing test means a publish can connect. When the fallback was needed the result
     * says so, since the stored setting is then wrong even though the test passed.
     */
    fun testFtp(credentials: FtpTestCredentials): TestConnectionResult {
        val settings = credentials.toConnectionSettings()
        val connection = try {
            ftpConnectionFactory.connectWithFallback(settings)
        } catch (e: FtpConnectionException) {
            log.warn("FTP connection test failed in {} phase: {}", e.phase, e.message)
            return TestConnectionResult(success = false, message = e.message ?: "Connection failed")
        } catch (e: Exception) {
            log.warn("FTP connection test failed: {}", e.message)
            return TestConnectionResult(success = false, message = e.message ?: "Connection failed")
        }

        try {
            val message = if (connection.fellBackFrom == null) {
                "Connected successfully"
            } else {
                "Connected successfully in ${connection.transferMode} mode. " +
                    "${connection.fellBackFrom} mode failed (${connection.fallbackReason}). " +
                    "Set the transfer mode to ${connection.transferMode} to match this network."
            }
            return TestConnectionResult(success = true, message = message)
        } finally {
            connection.client.disconnectQuietly()
        }
    }

    fun testSoundCloud(userId: String): TestConnectionResult {
        return try {
            val accessToken = soundCloudTokenManager.getValidAccessToken(userId)
            val me = soundCloudClient.getMe(accessToken)
            TestConnectionResult(
                success = true,
                message = "Connected as ${me.username}",
                quota = me.quota?.let {
                    mapOf(
                        "uploadSecondsUsed" to it.uploadSecondsUsed,
                        "uploadSecondsLeft" to it.uploadSecondsLeft
                    )
                }
            )
        } catch (e: IllegalStateException) {
            val message = e.message ?: "Connection failed"
            if (message.contains("No") || message.contains("not found", ignoreCase = true)) {
                TestConnectionResult(success = false, message = "No SoundCloud connection. Please authorize first.")
            } else if (message.contains("refresh failed", ignoreCase = true) || message.contains("expired", ignoreCase = true)) {
                TestConnectionResult(success = false, message = "SoundCloud authorization expired. Please re-authorize.")
            } else {
                TestConnectionResult(success = false, message = message)
            }
        } catch (e: Exception) {
            log.warn("SoundCloud connection test failed: {}", e.message)
            TestConnectionResult(success = false, message = e.message ?: "Connection failed")
        }
    }
}
