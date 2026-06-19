-- Dedup now runs on its own (stronger) model, separate from the per-article scoring (filter)
-- model. Track it per episode so the Costs tab labels the dedup stage with the actual model.
-- Nullable: legacy episodes (which scored + deduped on the shared filter model) keep NULL and
-- the Costs tab falls back to the filter model label for them.
ALTER TABLE episodes ADD COLUMN dedup_model TEXT;
