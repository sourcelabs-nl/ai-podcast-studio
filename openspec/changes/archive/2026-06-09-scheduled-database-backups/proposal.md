## Why

The application stores everything (podcasts, episodes, sources, articles, publications, settings) in a single SQLite database at `./data/ai-summary-podcast.db`. There is currently no backup: a corrupted file, a bad migration, or an accidental deletion loses all history. We want automatic, scheduled, compressed backups kept alongside the data, with the schedule adjustable from the settings page.

## What Changes

- Add a scheduled job that periodically writes a consistent, compressed backup of the database to a `backups/` folder under the data directory.
- The backup is produced with SQLite `VACUUM INTO` (a transactionally consistent, compact copy of schema + data — no half-written statements, safe with WAL/concurrent writes), then gzip-compressed to `ai-summary-podcast-yyyyMMdd-HHmmss.db.gz`.
- Backup configuration is **persisted in the database and editable at runtime from the settings page**: `enabled`, `cron` schedule, and `retentionCount` (older backups beyond this count are pruned). Changing the cron reschedules the job live — no restart required.
- The backup directory is a fixed filesystem setting from `application.yaml` (`app.backup.directory`, default `./data/backups`); the application-config values also seed the initial persisted settings.
- New admin API under `/admin/backup`: get/update settings (validates the cron), run a backup on demand, and list existing backups. A new "Backups" tab on the settings page exposes these (toggle, cron field with human-readable preview, retention, "Back up now", and the list of existing backups).

## Capabilities

### New Capabilities

- `database-backup`: scheduled, compressed, consistent SQLite backups with runtime-configurable schedule and retention.

## Impact

- New `backup_settings` table (Flyway `V59`) — single-row persisted config, seeded with defaults.
- `src/main/kotlin/com/aisummarypodcast/config/AppProperties.kt` (new `BackupProperties`: directory + seed defaults) and `application.yaml` (`app.backup.*`).
- New backend: `BackupSettings` entity + repository, `DatabaseBackupService` (VACUUM INTO + gzip + retention), `BackupSettingsService`, a dynamically-rescheduling `BackupScheduler` (Spring `TaskScheduler` + `CronTrigger`), and `BackupController` (`/admin/backup`).
- Frontend: a "Backups" tab on the settings page (reusing the existing `cronstrue`/`cron-parser` deps for the cron preview/validation).
- `README.md` (document backups + restore steps). Tests for the service, retention, cron validation, and reschedule.
