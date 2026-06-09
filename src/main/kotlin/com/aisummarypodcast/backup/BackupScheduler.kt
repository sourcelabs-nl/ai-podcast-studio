package com.aisummarypodcast.backup

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.support.CronTrigger
import org.springframework.stereotype.Component
import java.util.concurrent.ScheduledFuture

/**
 * Schedules the database backup according to the persisted [BackupSettings]. The schedule is
 * (re)applied on startup and whenever settings change, so cron edits take effect without a restart.
 */
@Component
class BackupScheduler(
    private val taskScheduler: TaskScheduler,
    private val backupSettingsService: BackupSettingsService,
    private val databaseBackupService: DatabaseBackupService
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private var future: ScheduledFuture<*>? = null

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() = reschedule()

    /** Cancels any existing schedule and re-schedules from current settings (or leaves it off). */
    @Synchronized
    fun reschedule() {
        future?.cancel(false)
        future = null

        val settings = backupSettingsService.get()
        if (!settings.enabled) {
            log.info("Scheduled database backups are disabled")
            return
        }
        val trigger = try {
            CronTrigger(settings.cron)
        } catch (e: Exception) {
            log.error("Invalid backup cron '{}' — not scheduling backups: {}", settings.cron, e.message)
            return
        }
        future = taskScheduler.schedule({ runSafely() }, trigger)
        log.info("Scheduled database backups with cron '{}'", settings.cron)
    }

    private fun runSafely() {
        try {
            databaseBackupService.backup()
        } catch (e: Exception) {
            log.error("Scheduled database backup failed: {}", e.message, e)
        }
    }
}
