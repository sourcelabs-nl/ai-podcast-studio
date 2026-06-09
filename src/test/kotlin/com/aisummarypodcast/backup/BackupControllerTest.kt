package com.aisummarypodcast.backup

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupControllerTest {

    private val settingsService = mockk<BackupSettingsService>(relaxed = true)
    private val scheduler = mockk<BackupScheduler>(relaxed = true)
    private val backupService = mockk<DatabaseBackupService>(relaxed = true)
    private val controller = BackupController(settingsService, scheduler, backupService)

    @Test
    fun `update rejects invalid cron with 400 and does not persist or reschedule`() {
        val response = controller.updateSettings(UpdateBackupSettingsRequest(true, "not a cron", 7))

        assertEquals(400, response.statusCode.value())
        verify(exactly = 0) { settingsService.update(any(), any(), any()) }
        verify(exactly = 0) { scheduler.reschedule() }
    }

    @Test
    fun `update rejects retentionCount below 1`() {
        val response = controller.updateSettings(UpdateBackupSettingsRequest(true, "0 0 2 * * *", 0))

        assertEquals(400, response.statusCode.value())
        verify(exactly = 0) { settingsService.update(any(), any(), any()) }
    }

    @Test
    fun `update persists valid settings and reschedules`() {
        every { settingsService.update(false, "0 0 4 * * *", 14) } returns
            BackupSettings(enabled = false, cron = "0 0 4 * * *", retentionCount = 14, updatedAt = "2026-06-09T00:00:00Z")

        val response = controller.updateSettings(UpdateBackupSettingsRequest(false, "0 0 4 * * *", 14))

        assertEquals(200, response.statusCode.value())
        verify { settingsService.update(false, "0 0 4 * * *", 14) }
        verify { scheduler.reschedule() }
        val body = response.body as BackupSettingsResponse
        assertEquals("0 0 4 * * *", body.cron)
        assertTrue(!body.enabled)
    }

    @Test
    fun `getSettings returns mapped settings`() {
        every { settingsService.get() } returns BackupSettings(enabled = true, cron = "0 0 2 * * *", retentionCount = 7)

        val body = controller.getSettings()

        assertEquals("0 0 2 * * *", body.cron)
        assertEquals(7, body.retentionCount)
    }
}
