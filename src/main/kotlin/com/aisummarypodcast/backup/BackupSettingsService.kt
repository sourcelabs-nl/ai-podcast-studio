package com.aisummarypodcast.backup

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Reads and updates the single persisted [BackupSettings] row. Cron validation lives at the API
 * boundary; this service only persists. Rescheduling is triggered by the caller after an update.
 */
@Service
class BackupSettingsService(private val repository: BackupSettingsRepository) {

    fun get(): BackupSettings =
        repository.findByIdOrNull(BackupSettings.SINGLETON_ID) ?: BackupSettings()

    fun update(enabled: Boolean, cron: String, retentionCount: Int): BackupSettings {
        val updated = get().copy(
            enabled = enabled,
            cron = cron,
            retentionCount = retentionCount,
            updatedAt = Instant.now().toString()
        )
        return repository.save(updated)
    }
}
