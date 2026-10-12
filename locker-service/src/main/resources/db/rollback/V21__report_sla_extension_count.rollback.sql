-- Manual rollback only.
ALTER TABLE locker_schema.locker_reports
    DROP COLUMN IF EXISTS sla_extension_count;
