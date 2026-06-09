package com.aisummarypodcast.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration
@EnableScheduling
@EnableConfigurationProperties(AppProperties::class)
class SchedulingConfig {

    /**
     * Dedicated scheduler for dynamically (re)scheduled jobs such as database backups, whose cron
     * is editable at runtime (see [com.aisummarypodcast.backup.BackupScheduler]). Also serves the
     * fixed `@Scheduled` annotation-driven tasks.
     */
    @Bean
    fun taskScheduler(): TaskScheduler = ThreadPoolTaskScheduler().apply {
        // 2 threads so a long-running backup does not stall other scheduled tasks (e.g. SSE heartbeat).
        poolSize = 2
        setThreadNamePrefix("scheduled-task-")
        initialize()
    }
}
