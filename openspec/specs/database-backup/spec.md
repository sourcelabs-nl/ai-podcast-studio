# database-backup Specification

## Purpose
TBD - created by archiving change scheduled-database-backups. Update Purpose after archive.
## Requirements
### Requirement: Scheduled compressed database backups
The system SHALL produce backups of the SQLite database as a consistent, compact copy created via `VACUUM INTO`, gzip-compressed and written to the configured backup directory (default `./data/backups`) as `ai-summary-podcast-<yyyyMMdd-HHmmss>.db.gz` (UTC timestamp). The copy SHALL be transactionally consistent (no partial/in-flight statements) and SHALL be created without requiring the application to stop.

Backups SHALL run on a configurable cron schedule when enabled. The schedule SHALL be evaluated by a managed task scheduler so that changing the schedule takes effect without restarting the application.

#### Scenario: Scheduled backup produces a valid compressed copy
- **WHEN** a scheduled backup runs while the application is active
- **THEN** a `*.db.gz` file is written to the backup directory that decompresses to a valid SQLite database containing the current schema and data

#### Scenario: Backups disabled
- **WHEN** backups are disabled in settings
- **THEN** no scheduled backup runs and no scheduled task is registered

### Requirement: Runtime-configurable backup settings
The system SHALL persist backup configuration (`enabled`, `cron`, `retentionCount`) in the database as a single-row settings record, seeded with application-configured defaults. The system SHALL expose this configuration for reading and updating via the admin API and the settings page. A cron expression SHALL be validated before being persisted; an invalid expression SHALL be rejected with a client error and SHALL NOT be saved. Updating the settings SHALL reschedule (or cancel) the backup job to match.

#### Scenario: Update cron reschedules without restart
- **WHEN** the cron schedule is changed and saved via the settings API
- **THEN** the new schedule is persisted and the backup job is rescheduled to the new cron without an application restart

#### Scenario: Invalid cron rejected
- **WHEN** an update is submitted with an invalid cron expression
- **THEN** the update is rejected with a 400 error and the stored settings are unchanged

### Requirement: Backup retention
The system SHALL retain only the most recent `retentionCount` backup files in the backup directory, deleting older ones after a successful backup.

#### Scenario: Old backups pruned
- **WHEN** a backup completes and more than `retentionCount` `*.db.gz` files exist
- **THEN** only the newest `retentionCount` files remain

### Requirement: On-demand backup and listing
The system SHALL provide an admin endpoint to trigger a backup immediately and an endpoint to list existing backups with their size and creation time.

#### Scenario: Manual backup
- **WHEN** an operator triggers a backup via the admin endpoint
- **THEN** a new `*.db.gz` is created and its path is returned

#### Scenario: List backups
- **WHEN** the list endpoint is called
- **THEN** it returns the existing backup files with name, size, and timestamp

