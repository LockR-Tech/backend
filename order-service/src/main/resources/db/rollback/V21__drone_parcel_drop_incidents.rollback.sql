-- Manual rollback only, after exporting incident data and stopping order-service.
-- Production rollback normally keeps these additive tables/columns to preserve audit history.
DROP TABLE IF EXISTS order_schema.drone_incident_resolution_proposals;
DROP TABLE IF EXISTS order_schema.drone_incident_timeline;
DROP TABLE IF EXISTS order_schema.drone_incident_evidence;
DROP TABLE IF EXISTS order_schema.drone_parcel_incidents;
ALTER TABLE order_schema.orders
    DROP COLUMN IF EXISTS incident_policy_version,
    DROP COLUMN IF EXISTS incident_compensation_enabled,
    DROP COLUMN IF EXISTS incident_compensation_rate,
    DROP COLUMN IF EXISTS incident_compensation_cap,
    DROP COLUMN IF EXISTS incident_free_redelivery,
    DROP COLUMN IF EXISTS incident_refund_shipping_fee,
    DROP COLUMN IF EXISTS incident_recovery_sla_hours,
    DROP COLUMN IF EXISTS incident_approval_required,
    DROP COLUMN IF EXISTS incident_dispute_allowed;
