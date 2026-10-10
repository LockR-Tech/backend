-- Reuse locker_reports for drone inspection tickets. The parcel-recovery state
-- remains in order-service, while this link keeps maintenance history attached to
-- the physical drone without duplicating the maintenance module.
ALTER TABLE locker_schema.locker_reports
    ADD COLUMN IF NOT EXISTS external_incident_id BIGINT,
    ADD COLUMN IF NOT EXISTS ticket_type VARCHAR(40);

CREATE UNIQUE INDEX IF NOT EXISTS uq_locker_reports_incident_ticket
    ON locker_schema.locker_reports (external_incident_id, ticket_type)
    WHERE external_incident_id IS NOT NULL AND ticket_type IS NOT NULL;

