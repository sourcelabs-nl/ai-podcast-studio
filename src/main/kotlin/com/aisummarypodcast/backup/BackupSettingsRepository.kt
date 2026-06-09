package com.aisummarypodcast.backup

import org.springframework.data.repository.CrudRepository

interface BackupSettingsRepository : CrudRepository<BackupSettings, Long>
