-- Manual rollback only; export linked report ids before running.
DROP INDEX IF EXISTS locker_schema.uq_locker_reports_incident_ticket;
ALTER TABLE locker_schema.locker_reports
    DROP COLUMN IF EXISTS external_incident_id,
    DROP COLUMN IF EXISTS ticket_type;

