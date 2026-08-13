package com.aisummarypodcast.publishing

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPSClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Duration
import javax.net.ssl.SSLException

class FtpConnectionFactoryTest {

    private val factory = FtpConnectionFactory()

    private val settings = FtpConnectionSettings(
        host = "ftp.example.com",
        port = 21,
        username = "user",
        password = "pass",
        useTls = false
    )

    /** A client that connects and logs in cleanly. */
    private fun happyClient(): FTPClient = mockk<FTPClient>(relaxed = true).also {
        every { it.replyCode } returns 220
        every { it.login(any(), any()) } returns true
    }

    @Test
    fun `passive mode is used by default`() {
        val client = happyClient()

        factory.connect(settings, client)

        verify { client.enterLocalPassiveMode() }
        verify(exactly = 0) { client.enterLocalActiveMode() }
    }

    @Test
    fun `active mode is used when configured`() {
        val client = happyClient()

        factory.connect(settings.copy(transferMode = FtpTransferMode.ACTIVE), client)

        verify { client.enterLocalActiveMode() }
        verify(exactly = 0) { client.enterLocalPassiveMode() }
    }

    @Test
    fun `connection is configured for binary transfers and NAT tolerance`() {
        val client = happyClient()

        factory.connect(settings, client)

        verify { client.setFileType(FTPClient.BINARY_FILE_TYPE) }
        verify { client.setUseEPSVwithIPv4(false) }
        verify { client.setRemoteVerificationEnabled(false) }
        verify { client.setDataTimeout(any<Duration>()) }
    }

    @Test
    fun `FTPS connection protects the data channel`() {
        val client = mockk<FTPSClient>(relaxed = true).also {
            every { it.replyCode } returns 220
            every { it.login(any(), any()) } returns true
        }

        factory.connect(settings.copy(useTls = true), client)

        verify { client.execPBSZ(0) }
        verify { client.execPROT("P") }
    }

    @Test
    fun `rejected login reports the authentication phase with the server reply`() {
        val client = mockk<FTPClient>(relaxed = true).also {
            every { it.replyCode } returns 220
            every { it.login(any(), any()) } returns false
            every { it.replyString } returns "530 Login incorrect."
            every { it.isConnected } returns true
        }

        val ex = assertThrows<FtpConnectionException> { factory.connect(settings, client) }

        assertEquals(FtpPhase.AUTHENTICATION, ex.phase)
        assertTrue(ex.message!!.contains("530 Login incorrect."), ex.message)
        assertTrue(ex.message!!.contains("user"), ex.message)
        verify { client.disconnect() }
    }

    @Test
    fun `timed out control connection blames the network and names the target`() {
        val client = mockk<FTPClient>(relaxed = true).also {
            every { it.connect(any<String>(), any()) } throws SocketTimeoutException("connect timed out")
        }

        val ex = assertThrows<FtpConnectionException> { factory.connect(settings, client) }

        assertEquals(FtpPhase.CONTROL_CONNECTION, ex.phase)
        assertTrue(ex.message!!.contains("ftp.example.com:21"), ex.message)
        assertTrue(ex.message!!.contains("blocked by this network"), ex.message)
        assertTrue(ex.message!!.contains("transfer mode cannot help"), ex.message)
    }

    @Test
    fun `unresolvable host is reported as such`() {
        val client = mockk<FTPClient>(relaxed = true).also {
            every { it.connect(any<String>(), any()) } throws UnknownHostException("ftp.example.com")
        }

        val ex = assertThrows<FtpConnectionException> { factory.connect(settings, client) }

        assertEquals(FtpPhase.CONTROL_CONNECTION, ex.phase)
        assertTrue(ex.message!!.contains("could not be resolved"), ex.message)
    }

    @Test
    fun `TLS negotiation failure is reported as a TLS problem`() {
        val client = mockk<FTPSClient>(relaxed = true).also {
            every { it.connect(any<String>(), any()) } throws SSLException("handshake_failure")
        }

        val ex = assertThrows<FtpConnectionException> { factory.connect(settings.copy(useTls = true), client) }

        assertEquals(FtpPhase.TLS, ex.phase)
        assertTrue(ex.message!!.contains("TLS negotiation"), ex.message)
    }

    // --- Data-channel verification and transfer-mode fallback ---
    // These stub the single-argument connect() on a spy, so the fallback logic is exercised without
    // opening sockets, while verifyDataChannel still runs for real against the mocked client.

    private val spiedFactory = spyk(FtpConnectionFactory())

    private fun clientWithWorkingDataChannel(): FTPClient = mockk<FTPClient>(relaxed = true).also {
        every { it.replyCode } returns 226
    }

    private fun clientWithBrokenDataChannel(reason: String): FTPClient = mockk<FTPClient>(relaxed = true).also {
        every { it.listFiles() } throws IOException(reason)
        every { it.isConnected } returns true
    }

    private fun stubConnect(mode: FtpTransferMode, client: FTPClient) {
        every { spiedFactory.connect(match<FtpConnectionSettings> { it.transferMode == mode }) } returns client
    }

    @Test
    fun `working data channel in the configured mode needs no fallback`() {
        stubConnect(FtpTransferMode.PASSIVE, clientWithWorkingDataChannel())

        val connection = spiedFactory.connectWithFallback(settings)

        assertEquals(FtpTransferMode.PASSIVE, connection.transferMode)
        assertEquals(null, connection.fellBackFrom)
        verify(exactly = 1) { spiedFactory.connect(any<FtpConnectionSettings>()) }
    }

    @Test
    fun `broken data channel falls back to the other transfer mode`() {
        val passiveClient = clientWithBrokenDataChannel("data connection refused")
        stubConnect(FtpTransferMode.PASSIVE, passiveClient)
        stubConnect(FtpTransferMode.ACTIVE, clientWithWorkingDataChannel())

        val connection = spiedFactory.connectWithFallback(settings)

        assertEquals(FtpTransferMode.ACTIVE, connection.transferMode)
        assertEquals(FtpTransferMode.PASSIVE, connection.fellBackFrom)
        assertTrue(connection.fallbackReason!!.contains("data connection refused"), connection.fallbackReason)
        // The abandoned connection must not be left open.
        verify { passiveClient.disconnect() }
    }

    @Test
    fun `control connection failure is not retried in the other mode`() {
        every { spiedFactory.connect(any<FtpConnectionSettings>()) } throws FtpConnectionException(
            FtpPhase.CONTROL_CONNECTION,
            "Control connection to ftp.example.com:21 timed out after 15s."
        )

        val ex = assertThrows<FtpConnectionException> { spiedFactory.connectWithFallback(settings) }

        assertEquals(FtpPhase.CONTROL_CONNECTION, ex.phase)
        verify(exactly = 1) { spiedFactory.connect(any<FtpConnectionSettings>()) }
    }

    @Test
    fun `failure in both transfer modes reports both attempts`() {
        stubConnect(FtpTransferMode.PASSIVE, clientWithBrokenDataChannel("passive refused"))
        stubConnect(FtpTransferMode.ACTIVE, clientWithBrokenDataChannel("active refused"))

        val ex = assertThrows<FtpConnectionException> { spiedFactory.connectWithFallback(settings) }

        assertEquals(FtpPhase.DATA_CHANNEL, ex.phase)
        assertTrue(ex.message!!.contains("both transfer modes"), ex.message)
        assertTrue(ex.message!!.contains("passive refused"), ex.message)
        assertTrue(ex.message!!.contains("active refused"), ex.message)
        verify(exactly = 2) { spiedFactory.connect(any<FtpConnectionSettings>()) }
    }

    @Test
    fun `non-positive listing reply counts as a data channel failure`() {
        val client = mockk<FTPClient>(relaxed = true).also {
            every { it.replyCode } returns 425
            every { it.replyString } returns "425 Unable to build data connection."
        }

        val ex = assertThrows<FtpConnectionException> { factory.verifyDataChannel(client) }

        assertEquals(FtpPhase.DATA_CHANNEL, ex.phase)
        assertTrue(ex.message!!.contains("425"), ex.message)
    }

    @Test
    fun `greeting that is not a positive completion aborts the connection`() {
        val client = mockk<FTPClient>(relaxed = true).also {
            every { it.replyCode } returns 421
            every { it.replyString } returns "421 Too many connections."
            every { it.isConnected } returns true
        }

        val ex = assertThrows<FtpConnectionException> { factory.connect(settings, client) }

        assertEquals(FtpPhase.CONTROL_CONNECTION, ex.phase)
        assertTrue(ex.message!!.contains("421 Too many connections."), ex.message)
        verify(exactly = 0) { client.login(any(), any()) }
        verify { client.disconnect() }
    }
}
