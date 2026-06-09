## Context

Single-file SQLite DB in WAL mode at `./data/ai-summary-podcast.db`, Hikari pool, `@EnableScheduling` already on (`EpisodeCleanup`, `LlmCacheCleanup` use static `@Scheduled(cron=...)`). The data dir is `appProperties.episodes.directory` (`./data`). Global settings today are either static (`application.yaml`) or user-scoped (`api_keys`); there is no system-settings store. A DB backup is system-wide (one shared file), so its config is system-level, exposed under `/admin/...` like the existing maintenance endpoints.

## Goals / Non-Goals

**Goals:** consistent, compressed, scheduled backups in the data folder; schedule + retention editable at runtime from the settings page; live reschedule on change; on-demand trigger; bounded disk via retention.

**Non-Goals:** off-site/remote upload, point-in-time/WAL archiving, automated restore (documented manual steps), backing up audio/feed files (separately reproducible), per-user backup config (it is system-wide).

## Decisions

- **`VACUUM INTO` then gzip.** `VACUUM INTO '<tmp>.db'` over a pooled connection writes a complete, consistent, defragmented copy inside a read transaction (no partial statements; waits on `busy_timeout` under write load). Stream the finished file through `GZIPOutputStream` to `<name>.db.gz`, delete the temp. Compressing only after the copy completes guarantees the archive wraps a whole, valid DB.
- **Persisted single-row settings.** New `backup_settings` table (one row) with `enabled`, `cron`, `retention_count`, optimistic `version`. `V59` migration creates it and seeds a default row (from the `app.backup.*` defaults). `BackupSettingsService` reads/updates it. The DB row is the source of truth; `application.yaml` only supplies the directory and the seed defaults.
- **Dynamic rescheduling.** `BackupScheduler` holds a `TaskScheduler` and a `ScheduledFuture?`. On `ApplicationReadyEvent` and after every settings update it `reschedule()`s: cancel the current future, and if `enabled`, schedule the backup task with `CronTrigger(cron)`; if disabled, leave it cancelled. This makes cron edits take effect without a restart. A dedicated single-thread `ThreadPoolTaskScheduler` bean is defined in `SchedulingConfig`.
- **Cron validation.** `PUT /admin/backup/settings` validates the cron with Spring `CronExpression.parse(...)` and rejects invalid expressions (400) before persisting, so a bad cron can never break scheduling.
- **Single entry point.** `DatabaseBackupService.backup()` is called by both the scheduled task and `POST /admin/backup`. Naming `ai-summary-podcast-yyyyMMdd-HHmmss.db.gz` (UTC); retention lists `*.db.gz` sorted by name and deletes all but the newest `retentionCount`.
- **Frontend.** A "Backups" tab on the settings page: enabled toggle, cron text field with `cronstrue` human-readable + `cron-parser` next-run preview and inline validity, retention number, Save (PUT), a "Back up now" button (POST), and a list of existing backups (name, size, date) from `GET /admin/backup`.

## Risks / Trade-offs

- [Backup during heavy writes] → `VACUUM INTO` waits on `busy_timeout`; on failure it logs and the next scheduled run retries.
- [Invalid cron persisted] → prevented by server-side validation; the scheduler also guards parse failures by logging and not scheduling.
- [Disk usage] → retention prunes to N gzipped copies (small relative to audio).
- [Binary format] → restore is `gunzip` + replace the DB file while stopped (documented). User confirmed a compact binary copy is acceptable.
