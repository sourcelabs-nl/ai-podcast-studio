package com.aisummarypodcast.backup

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

/**
 * Runtime-editable scheduled-backup configuration. Persisted as a single row (id = [SINGLETON_ID]);
 * the row is seeded by Flyway and is the source of truth, edited from the settings page.
 */
@Table("backup_settings")
data class BackupSettings(
    @Id val id: Long = SINGLETON_ID,
    val enabled: Boolean = true,
    val cron: String = "0 0 2 * * *",
    val retentionCount: Int = 7,
    val updatedAt: String? = null
) {
    companion object {
        const val SINGLETON_ID = 1L
    }
}
