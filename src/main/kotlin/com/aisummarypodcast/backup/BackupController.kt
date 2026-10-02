package com.aisummarypodcast.backup

import com.aisummarypodcast.api.InvalidRequestException
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.support.CronExpression
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/backup")
class BackupController(
    private val backupSettingsService: BackupSettingsService,
    private val backupScheduler: BackupScheduler,
    private val databaseBackupService: DatabaseBackupService
) {

    @GetMapping("/settings")
    fun getSettings(): BackupSettingsResponse = backupSettingsService.get().toResponse()

    @PutMapping("/settings")
    fun updateSettings(@RequestBody request: UpdateBackupSettingsRequest): ResponseEntity<Any> {
        if (!CronExpression.isValidExpression(request.cron)) throw InvalidRequestException("Invalid cron expression: ${request.cron}")
        if (request.retentionCount < 1) throw InvalidRequestException("retentionCount must be at least 1")
        val updated = backupSettingsService.update(request.enabled, request.cron, request.retentionCount)
        backupScheduler.reschedule()
        return ResponseEntity.ok(updated.toResponse())
    }

    @PostMapping
    fun runNow(): ResponseEntity<Any> {
        val path = databaseBackupService.backup()
        return ResponseEntity.ok(mapOf("path" to path.toString(), "backups" to databaseBackupService.list()))
    }

    @GetMapping
    fun list(): List<BackupInfo> = databaseBackupService.list()
}
