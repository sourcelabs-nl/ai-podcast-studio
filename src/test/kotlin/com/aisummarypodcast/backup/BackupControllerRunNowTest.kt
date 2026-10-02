package com.aisummarypodcast.backup

import com.aisummarypodcast.api.OperationFailedException
import com.aisummarypodcast.config.AppProperties
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Path

@WebMvcTest(BackupController::class)
class BackupControllerRunNowTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @MockkBean private lateinit var backupSettingsService: BackupSettingsService
    @MockkBean(relaxed = true) private lateinit var backupScheduler: BackupScheduler
    @MockkBean private lateinit var databaseBackupService: DatabaseBackupService

    // WebConfig sits in the sliced context and needs it; the controller itself does not use it.
    @MockkBean(relaxed = true) private lateinit var appProperties: AppProperties

    @Test
    fun `run now returns the path and the backups`() {
        every { databaseBackupService.backup() } returns Path.of("/data/backups/b.db.gz")
        every { databaseBackupService.list() } returns listOf(BackupInfo("b.db.gz", 10, "2026-10-02T00:00:00Z"))

        mockMvc.perform(post("/admin/backup"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.path").value("/data/backups/b.db.gz"))
            .andExpect(jsonPath("$.backups[0].name").value("b.db.gz"))
    }

    @Test
    fun `run now answers 500 when the backup fails`() {
        every { databaseBackupService.backup() } throws
            OperationFailedException("Backup failed: disk full", RuntimeException("disk full"))

        mockMvc.perform(post("/admin/backup"))
            .andExpect(status().isInternalServerError)
            .andExpect(content().json("""{"error":"Backup failed: disk full"}""", true))
    }
}
