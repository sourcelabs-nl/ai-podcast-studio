-- Single-row table holding the runtime-editable scheduled-backup configuration.
CREATE TABLE backup_settings (
    id              INTEGER PRIMARY KEY,
    enabled         INTEGER NOT NULL DEFAULT 1,
    cron            TEXT    NOT NULL DEFAULT '0 0 2 * * *',
    retention_count INTEGER NOT NULL DEFAULT 7,
    updated_at      TEXT
);

-- Seed the single configuration row with the application defaults.
INSERT INTO backup_settings (id, enabled, cron, retention_count, updated_at)
VALUES (1, 1, '0 0 2 * * *', 7, NULL);
