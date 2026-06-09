package com.aisummarypodcast.backup

import com.aisummarypodcast.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPOutputStream
import javax.sql.DataSource
import kotlin.io.path.name

/**
 * Produces compressed, consistent SQLite backups using `VACUUM INTO` (a transactionally consistent,
 * compact copy of schema + data — safe while the app reads/writes) and gzip. After each backup it
 * prunes older files beyond the configured retention count.
 */
@Service
class DatabaseBackupService(
    private val dataSource: DataSource,
    private val appProperties: AppProperties,
    private val backupSettingsService: BackupSettingsService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val timestampFormat = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

    companion object {
        private const val PREFIX = "ai-summary-podcast-"
        private const val SUFFIX = ".db.gz"
        private val FILE_PATTERN = Regex("^${Regex.escape(PREFIX)}\\d{8}-\\d{6}${Regex.escape(SUFFIX)}$")
    }

    /** Creates a new compressed backup and prunes old ones. Returns the created `.db.gz` path. */
    fun backup(): Path {
        val dir = Path.of(appProperties.backup.directory)
        Files.createDirectories(dir)

        val timestamp = timestampFormat.format(Instant.now())
        val tempDb = dir.resolve("$PREFIX$timestamp.db")
        val target = dir.resolve("$PREFIX$timestamp$SUFFIX")

        try {
            vacuumInto(tempDb)
            gzip(tempDb, target)
        } finally {
            Files.deleteIfExists(tempDb)
        }

        prune(dir, backupSettingsService.get().retentionCount)
        log.info("Database backup written to {} ({} bytes)", target, Files.size(target))
        return target
    }

    private fun vacuumInto(target: Path) {
        // VACUUM INTO must run outside a transaction; Hikari connections are autoCommit by default.
        val escaped = target.toAbsolutePath().toString().replace("'", "''")
        dataSource.connection.use { conn ->
            conn.createStatement().use { st ->
                st.execute("VACUUM INTO '$escaped'")
            }
        }
    }

    private fun gzip(source: Path, target: Path) {
        Files.newInputStream(source).use { input ->
            GZIPOutputStream(Files.newOutputStream(target)).use { gz ->
                input.copyTo(gz)
            }
        }
    }

    /** Lists existing backup files, newest first, with size and last-modified time. */
    fun list(): List<BackupInfo> {
        val dir = Path.of(appProperties.backup.directory)
        if (!Files.isDirectory(dir)) return emptyList()
        return Files.list(dir).use { stream ->
            stream.filter { FILE_PATTERN.matches(it.name) }
                .map { path ->
                    BackupInfo(
                        name = path.name,
                        sizeBytes = Files.size(path),
                        createdAt = Files.getLastModifiedTime(path).toInstant().toString()
                    )
                }
                .sorted(Comparator.comparing<BackupInfo, String> { it.name }.reversed())
                .toList()
        }
    }

    /** Keeps the newest [retentionCount] backup files (by timestamped name) and deletes the rest. */
    fun prune(dir: Path, retentionCount: Int) {
        if (retentionCount <= 0 || !Files.isDirectory(dir)) return
        val backups = Files.list(dir).use { stream ->
            stream.filter { FILE_PATTERN.matches(it.name) }
                .sorted(Comparator.comparing<Path, String> { it.name }.reversed())
                .toList()
        }
        backups.drop(retentionCount).forEach { old ->
            try {
                Files.deleteIfExists(old)
                log.info("Pruned old backup {}", old.fileName)
            } catch (e: Exception) {
                log.warn("Failed to prune old backup {}: {}", old.fileName, e.message)
            }
        }
    }
}
