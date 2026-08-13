package com.aisummarypodcast.publishing

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.commons.net.ftp.FTPClient
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PublishingTestServiceFtpTest {

    private val tokenManager = mockk<SoundCloudTokenManager>()
    private val soundCloudClient = mockk<SoundCloudClient>()
    private val connectionFactory = mockk<FtpConnectionFactory>()
    private val service = PublishingTestService(tokenManager, soundCloudClient, connectionFactory)

    private val credentials = FtpTestCredentials(
        host = "ftp.example.com",
        username = "user",
        password = "pass",
        useTls = true
    )

    private val client = mockk<FTPClient>(relaxed = true).also {
        every { it.isConnected } returns true
    }

    @Test
    fun `connection in the configured mode reports plain success`() {
        every { connectionFactory.connectWithFallback(any()) } returns
            FtpConnection(client, FtpTransferMode.PASSIVE)

        val result = service.testFtp(credentials)

        assertTrue(result.success)
        assertTrue(result.message.contains("Connected successfully"), result.message)
        verify { client.disconnect() }
    }

    @Test
    fun `fallback is reported as success naming the mode that worked`() {
        every { connectionFactory.connectWithFallback(any()) } returns FtpConnection(
            client = client,
            transferMode = FtpTransferMode.ACTIVE,
            fellBackFrom = FtpTransferMode.PASSIVE,
            fallbackReason = "Data connection failed: connection refused"
        )

        val result = service.testFtp(credentials)

        assertTrue(result.success)
        assertTrue(result.message.contains("ACTIVE mode"), result.message)
        assertTrue(result.message.contains("PASSIVE mode failed"), result.message)
        assertTrue(result.message.contains("connection refused"), result.message)
    }

    @Test
    fun `connection failure is reported with the factory's phase-classified message`() {
        every { connectionFactory.connectWithFallback(any()) } throws FtpConnectionException(
            FtpPhase.CONTROL_CONNECTION,
            "Control connection to ftp.example.com:21 timed out after 15s."
        )

        val result = service.testFtp(credentials)

        assertFalse(result.success)
        assertTrue(result.message.contains("timed out"), result.message)
    }

    @Test
    fun `requested transfer mode is passed through to the connection`() {
        every { connectionFactory.connectWithFallback(any()) } returns
            FtpConnection(client, FtpTransferMode.ACTIVE)

        service.testFtp(credentials.copy(transferMode = "active"))

        verify { connectionFactory.connectWithFallback(match { it.transferMode == FtpTransferMode.ACTIVE }) }
    }
}
