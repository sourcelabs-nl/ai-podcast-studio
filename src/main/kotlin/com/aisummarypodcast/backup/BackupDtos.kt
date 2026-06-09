package com.aisummarypodcast.backup

/** Request to update the persisted backup settings. */
data class UpdateBackupSettingsRequest(
    val enabled: Boolean,
    val cron: String,
    val retentionCount: Int
)

/** Backup settings as returned to the client. */
data class BackupSettingsResponse(
    val enabled: Boolean,
    val cron: String,
    val retentionCount: Int,
    val updatedAt: String?
)

/** Metadata for an existing backup file on disk. */
data class BackupInfo(
    val name: String,
    val sizeBytes: Long,
    val createdAt: String
)

fun BackupSettings.toResponse(): BackupSettingsResponse =
    BackupSettingsResponse(enabled = enabled, cron = cron, retentionCount = retentionCount, updatedAt = updatedAt)
