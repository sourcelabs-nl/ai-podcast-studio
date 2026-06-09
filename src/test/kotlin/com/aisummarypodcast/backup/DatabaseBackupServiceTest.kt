package com.aisummarypodcast.backup

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.BackupProperties
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.sqlite.SQLiteDataSource
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import kotlin.io.path.name

class DatabaseBackupServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var backupDir: Path
    private lateinit var dbFile: Path
    private lateinit var dataSource: SQLiteDataSource
    private val appProperties = mockk<AppProperties>()
    private val backupSettingsService = mockk<BackupSettingsService>()
    private lateinit var service: DatabaseBackupService

    @BeforeEach
    fun setup() {
        backupDir = tempDir.resolve("backups")
        dbFile = tempDir.resolve("test.db")
        dataSource = SQLiteDataSource().apply { url = "jdbc:sqlite:$dbFile" }
        dataSource.connection.use { conn ->
            conn.createStatement().use { st ->
                st.execute("CREATE TABLE widget (id INTEGER PRIMARY KEY, name TEXT)")
                st.execute("INSERT INTO widget (name) VALUES ('alpha'), ('beta')")
            }
        }
        every { appProperties.backup } returns BackupProperties(directory = backupDir.toString())
        every { backupSettingsService.get() } returns BackupSettings(retentionCount = 7)
        service = DatabaseBackupService(dataSource, appProperties, backupSettingsService)
    }

    @Test
    fun `backup creates a gzipped copy that decompresses to a valid database with the data`() {
        val gz = service.backup()

        assertTrue(Files.exists(gz), "backup file should exist")
        assertTrue(gz.name.matches(Regex("ai-summary-podcast-\\d{8}-\\d{6}\\.db\\.gz")), "unexpected name: ${gz.name}")
        assertTrue(Files.size(gz) > 0, "backup should not be empty")
        // The uncompressed temp .db must be cleaned up
        assertTrue(Files.list(backupDir).use { s -> s.noneMatch { it.name.endsWith(".db") } }, "temp .db should be removed")

        val restored = tempDir.resolve("restored.db")
        GZIPInputStream(Files.newInputStream(gz)).use { input -> Files.copy(input, restored) }

        val restoredDs = SQLiteDataSource().apply { url = "jdbc:sqlite:$restored" }
        restoredDs.connection.use { conn ->
            conn.createStatement().use { st ->
                val rs = st.executeQuery("SELECT count(*) FROM widget")
                rs.next()
                assertEquals(2, rs.getInt(1), "restored DB should contain the seeded rows")
            }
        }
    }

    @Test
    fun `prune keeps only the newest retentionCount backups`() {
        Files.createDirectories(backupDir)
        // Older timestamps sort before newer ones lexicographically
        val names = (1..10).map { "ai-summary-podcast-202601%02d-000000.db.gz".format(it) }
        names.forEach { Files.write(backupDir.resolve(it), byteArrayOf(1)) }

        service.prune(backupDir, 7)

        val remaining = Files.list(backupDir).use { s -> s.map { it.name }.sorted().toList() }
        assertEquals(7, remaining.size)
        // The 3 oldest (day 01..03) should be gone; the newest 7 (04..10) remain
        assertTrue(remaining.none { it.contains("20260101") || it.contains("20260102") || it.contains("20260103") })
        assertTrue(remaining.contains("ai-summary-podcast-20260110-000000.db.gz"))
    }

    @Test
    fun `list returns existing backups newest first`() {
        Files.createDirectories(backupDir)
        listOf(
            "ai-summary-podcast-20260101-000000.db.gz",
            "ai-summary-podcast-20260105-000000.db.gz",
            "ai-summary-podcast-20260103-000000.db.gz",
            "not-a-backup.txt"
        ).forEach { Files.write(backupDir.resolve(it), byteArrayOf(1)) }

        val list = service.list()

        assertEquals(3, list.size, "only matching backup files are listed")
        assertEquals("ai-summary-podcast-20260105-000000.db.gz", list.first().name, "newest first")
    }
}
