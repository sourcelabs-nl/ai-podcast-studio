package com.aisummarypodcast.publishing

import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Duration
import javax.net.ssl.SSLException

/**
 * The single place an FTP(S) connection is opened. Publishing and connection testing both come
 * through here so that a successful connection test means publishing can connect the same way.
 *
 * Failures are tagged with the [FtpPhase] they happened in: a blocked control port, a rejected
 * login, and a firewalled data channel need very different responses from the user, and a bare
 * "Connect timed out" tells them apart from nothing.
 */
@Component
class FtpConnectionFactory {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val CONNECT_TIMEOUT = Duration.ofSeconds(15)

        /** Caps a stalled transfer. Without it, a silently dropped data port hangs indefinitely. */
        private val DATA_TIMEOUT = Duration.ofSeconds(60)

        /** NOOPs during long uploads, so idle-timeout firewalls don't drop the control channel. */
        private val CONTROL_KEEP_ALIVE = Duration.ofSeconds(30)
    }

    /**
     * Opens a connection whose data channel is known to work: the configured transfer mode is tried
     * first and, if only the data connection is the problem, the other mode is tried instead.
     *
     * The data channel is verified with a directory listing before returning, so callers upload over
     * a proven channel and a fallback never has to abandon a half-written file. Failures that a
     * transfer mode cannot influence (blocked control port, bad credentials, TLS) are not retried.
     */
    fun connectWithFallback(settings: FtpConnectionSettings): FtpConnection {
        val primaryFailure = try {
            return FtpConnection(openVerified(settings), settings.transferMode)
        } catch (e: FtpConnectionException) {
            if (e.phase != FtpPhase.DATA_CHANNEL) throw e
            e
        }

        val fallbackMode = settings.transferMode.opposite()
        log.warn(
            "FTP data connection failed in {} mode ({}), retrying in {} mode",
            settings.transferMode, primaryFailure.message, fallbackMode
        )
        try {
            return FtpConnection(
                client = openVerified(settings.copy(transferMode = fallbackMode)),
                transferMode = fallbackMode,
                fellBackFrom = settings.transferMode,
                fallbackReason = primaryFailure.message
            )
        } catch (e: FtpConnectionException) {
            if (e.phase != FtpPhase.DATA_CHANNEL) throw e
            throw FtpConnectionException(
                FtpPhase.DATA_CHANNEL,
                "Data connection failed in both transfer modes. " +
                    "${settings.transferMode}: ${primaryFailure.message}; $fallbackMode: ${e.message}",
                e
            )
        }
    }

    /** Opens, authenticates and configures a connection. The caller owns disconnecting it. */
    fun connect(settings: FtpConnectionSettings): FTPClient =
        connect(settings, newClient(settings.useTls))

    /** Testing seam: same logic against a supplied client, so no live server is needed. */
    internal fun connect(settings: FtpConnectionSettings, client: FTPClient): FTPClient {
        try {
            openControlChannel(client, settings)
            authenticate(client, settings)
            protectDataChannel(client)
            configureTransfer(client, settings)
            log.info(
                "FTP connected to {}:{} (tls={}, mode={})",
                settings.host, settings.port, settings.useTls, settings.transferMode
            )
            return client
        } catch (e: Throwable) {
            client.disconnectQuietly()
            throw e
        }
    }

    private fun newClient(useTls: Boolean): FTPClient = if (useTls) FTPSClient() else FTPClient()

    private fun openVerified(settings: FtpConnectionSettings): FTPClient {
        val client = connect(settings)
        try {
            verifyDataChannel(client)
        } catch (e: Throwable) {
            client.disconnectQuietly()
            throw e
        }
        return client
    }

    /**
     * Lists the working directory to prove a data connection can be opened. Listing is idempotent, so
     * paying for it up front is what makes falling back to the other transfer mode safe.
     */
    internal fun verifyDataChannel(client: FTPClient) {
        try {
            client.listFiles()
        } catch (e: IOException) {
            throw FtpConnectionException(FtpPhase.DATA_CHANNEL, "Data connection failed: ${e.message}", e)
        }
        if (!FTPReply.isPositiveCompletion(client.replyCode)) {
            throw FtpConnectionException(
                FtpPhase.DATA_CHANNEL,
                "Data connection failed: ${client.replyString?.trim()}"
            )
        }
    }

    private fun openControlChannel(client: FTPClient, settings: FtpConnectionSettings) {
        client.connectTimeout = CONNECT_TIMEOUT.toMillis().toInt()
        val target = "${settings.host}:${settings.port}"
        try {
            client.connect(settings.host, settings.port)
        } catch (e: UnknownHostException) {
            throw FtpConnectionException(
                FtpPhase.CONTROL_CONNECTION,
                "Hostname ${settings.host} could not be resolved.",
                e
            )
        } catch (e: SocketTimeoutException) {
            throw FtpConnectionException(FtpPhase.CONTROL_CONNECTION, blockedPortMessage(target), e)
        } catch (e: ConnectException) {
            // "Connection timed out" also arrives as a ConnectException on some stacks.
            throw FtpConnectionException(
                FtpPhase.CONTROL_CONNECTION,
                if (e.message?.contains("timed out", ignoreCase = true) == true) {
                    blockedPortMessage(target)
                } else {
                    "Control connection to $target was refused. Check the host and port, " +
                        "or whether the server is listening there."
                },
                e
            )
        } catch (e: SSLException) {
            throw FtpConnectionException(
                FtpPhase.TLS,
                "TLS negotiation with $target failed: ${e.message}. " +
                    "If the server does not support explicit TLS on this port, turn off TLS or use the implicit FTPS port.",
                e
            )
        } catch (e: IOException) {
            throw FtpConnectionException(
                FtpPhase.CONTROL_CONNECTION,
                "Control connection to $target failed: ${e.message}",
                e
            )
        }

        if (!FTPReply.isPositiveCompletion(client.replyCode)) {
            throw FtpConnectionException(
                FtpPhase.CONTROL_CONNECTION,
                "Server at $target refused the connection: ${client.replyString?.trim()}"
            )
        }
    }

    private fun blockedPortMessage(target: String) =
        "Control connection to $target timed out after ${CONNECT_TIMEOUT.toSeconds()}s. " +
            "The FTP port is most likely blocked by this network (firewall or proxy); " +
            "changing the transfer mode cannot help, since the connection never opens."

    private fun authenticate(client: FTPClient, settings: FtpConnectionSettings) {
        val loggedIn = try {
            client.login(settings.username, settings.password)
        } catch (e: IOException) {
            throw FtpConnectionException(
                FtpPhase.AUTHENTICATION,
                "Login to ${settings.host} failed: ${e.message}",
                e
            )
        }
        if (!loggedIn) {
            throw FtpConnectionException(
                FtpPhase.AUTHENTICATION,
                "FTP authentication failed for user '${settings.username}': ${client.replyString?.trim()}"
            )
        }
    }

    /**
     * Switches the FTPS data channel to encrypted. Without PBSZ/PROT the files themselves travel in
     * the clear and servers that require protection reject the transfer (522).
     */
    private fun protectDataChannel(client: FTPClient) {
        if (client !is FTPSClient) return
        try {
            client.execPBSZ(0)
            client.execPROT("P")
        } catch (e: IOException) {
            throw FtpConnectionException(
                FtpPhase.DATA_CHANNEL,
                "Server rejected data channel protection (PBSZ/PROT P): ${e.message}",
                e
            )
        }
    }

    private fun configureTransfer(client: FTPClient, settings: FtpConnectionSettings) {
        when (settings.transferMode) {
            FtpTransferMode.PASSIVE -> client.enterLocalPassiveMode()
            FtpTransferMode.ACTIVE -> client.enterLocalActiveMode()
        }
        client.setFileType(FTPClient.BINARY_FILE_TYPE)
        client.setDataTimeout(DATA_TIMEOUT)
        client.setControlKeepAliveTimeout(CONTROL_KEEP_ALIVE)
        // Some proxies mangle EPSV replies; plain PASV is the safer default on IPv4.
        client.setUseEPSVwithIPv4(false)
        // A server behind NAT advertises its private address in the PASV reply. Verifying that the
        // data host matches the control host would reject those connections outright.
        client.setRemoteVerificationEnabled(false)
    }
}
