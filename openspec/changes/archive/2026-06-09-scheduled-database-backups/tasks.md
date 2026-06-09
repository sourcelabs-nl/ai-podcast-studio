## 1. Persistence + config

- [x] 1.1 Add `BackupProperties(enabled, cron, directory, retentionCount)` to `AppProperties` and `app.backup.*` to `application.yaml`
- [x] 1.2 Flyway `V59__add_backup_settings.sql`: create single-row `backup_settings` table and seed a default row
- [x] 1.3 `BackupSettings` entity + `BackupSettingsRepository`; `BackupSettingsService` to read/update the row

## 2. Backup engine

- [x] 2.1 `DatabaseBackupService.backup()`: `VACUUM INTO` a temp `.db` in the backup dir, gzip to `ai-summary-podcast-yyyyMMdd-HHmmss.db.gz`, delete the temp
- [x] 2.2 Retention: keep the newest `retentionCount` `*.db.gz`, delete the rest
- [x] 2.3 Unit tests against a temp file SQLite: backup produces a valid, decompressible SQLite DB containing the data; retention prunes correctly

## 3. Scheduling

- [x] 3.1 Define a `ThreadPoolTaskScheduler` bean in `SchedulingConfig`
- [x] 3.2 `BackupScheduler`: schedule from settings on `ApplicationReadyEvent`; `reschedule()` cancels + reschedules with `CronTrigger` when enabled, cancels when disabled; guard cron parse failures
- [x] 3.3 Settings updates trigger `reschedule()`

## 4. API

- [x] 4.1 `BackupController` under `/admin/backup`: `GET/PUT /settings` (validate cron with `CronExpression.parse`, reschedule on save), `POST /` (run now), `GET /` (list existing backups with size + timestamp)
- [x] 4.2 Controller/validation tests (invalid cron rejected with 400)

## 5. Frontend

- [x] 5.1 "Backups" tab on the settings page: enabled toggle, cron field with `cronstrue` preview + `cron-parser` next-run + validity, retention input, Save
- [x] 5.2 "Back up now" button and a list of existing backups (name, size, date)

## 6. Docs + verify

- [x] 6.1 README: document scheduled backups, settings, and restore steps
- [x] 6.2 `mvn test` green; restart; trigger a manual backup and confirm a valid `.db.gz` appears and retention works; adjust cron in the UI and confirm it reschedules
