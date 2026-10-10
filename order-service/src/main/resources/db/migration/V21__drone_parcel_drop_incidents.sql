-- Drone parcel-drop incidents cannot be represented by order_complaints: they need
-- machine-enforced flight/recovery/payment state, immutable telemetry evidence and
-- versioned customer resolution proposals. All changes are additive.
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS incident_policy_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS incident_compensation_enabled BOOLEAN,
    ADD COLUMN IF NOT EXISTS incident_compensation_rate NUMERIC(5, 2),
    ADD COLUMN IF NOT EXISTS incident_compensation_cap NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS incident_free_redelivery BOOLEAN,
    ADD COLUMN IF NOT EXISTS incident_refund_shipping_fee BOOLEAN,
    ADD COLUMN IF NOT EXISTS incident_recovery_sla_hours INTEGER,
    ADD COLUMN IF NOT EXISTS incident_approval_required BOOLEAN,
    ADD COLUMN IF NOT EXISTS incident_dispute_allowed BOOLEAN;

CREATE TABLE IF NOT EXISTS order_schema.drone_parcel_incidents
(
    id BIGSERIAL PRIMARY KEY,
    incident_code VARCHAR(64) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL UNIQUE,
    mission_id BIGINT NOT NULL UNIQUE,
    drone_unit_id BIGINT NOT NULL,
    drone_code VARCHAR(80) NOT NULL,
    reported_by_user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(50) NOT NULL,
    parcel_status VARCHAR(40) NOT NULL,
    recovery_status VARCHAR(40) NOT NULL,
    inspection_status VARCHAR(40) NOT NULL,
    redelivery_status VARCHAR(40) NOT NULL,
    compensation_status VARCHAR(40) NOT NULL,
    return_flight_status VARCHAR(50) NOT NULL,
    drop_latitude DOUBLE PRECISION,
    drop_longitude DOUBLE PRECISION,
    gps_accuracy_m DOUBLE PRECISION,
    gps_source VARCHAR(30) NOT NULL,
    telemetry_observed_at TIMESTAMP WITH TIME ZONE,
    telemetry_stale BOOLEAN NOT NULL DEFAULT FALSE,
    telemetry_json TEXT,
    camera_status VARCHAR(30) NOT NULL,
    camera_snapshot_url VARCHAR(1000),
    inspection_report_id BIGINT,
    recovery_assigned_to_user_id BIGINT,
    recovery_locker_id BIGINT,
    recovery_started_at TIMESTAMP,
    recovery_submitted_at TIMESTAMP,
    recovery_verified_at TIMESTAMP,
    recovery_outcome VARCHAR(30),
    parcel_condition VARCHAR(30),
    recovery_note VARCHAR(2000),
    recovered_latitude DOUBLE PRECISION,
    recovered_longitude DOUBLE PRECISION,
    recovered_gps_accuracy_m DOUBLE PRECISION,
    returned_to_hub_at TIMESTAMP,
    returned_to_hub_by_user_id BIGINT,
    redelivery_order_id BIGINT,
    compensation_reference VARCHAR(160),
    resolved_at TIMESTAMP,
    closed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_drone_drop_incident_status
    ON order_schema.drone_parcel_incidents (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_drone_drop_incident_recovery_assignee
    ON order_schema.drone_parcel_incidents (recovery_assigned_to_user_id, recovery_status);

CREATE TABLE IF NOT EXISTS order_schema.drone_incident_evidence
(
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES order_schema.drone_parcel_incidents(id),
    stage VARCHAR(30) NOT NULL,
    public_id VARCHAR(255) NOT NULL UNIQUE,
    secure_url VARCHAR(1000) NOT NULL,
    caption VARCHAR(500),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    gps_accuracy_m DOUBLE PRECISION,
    captured_at TIMESTAMP WITH TIME ZONE,
    uploaded_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_drone_incident_evidence_incident
    ON order_schema.drone_incident_evidence (incident_id, created_at);

CREATE TABLE IF NOT EXISTS order_schema.drone_incident_timeline
(
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES order_schema.drone_parcel_incidents(id),
    event_type VARCHAR(60) NOT NULL,
    from_status VARCHAR(50),
    to_status VARCHAR(50),
    actor_user_id BIGINT,
    note VARCHAR(2000),
    metadata_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_drone_incident_timeline_incident
    ON order_schema.drone_incident_timeline (incident_id, created_at);

CREATE TABLE IF NOT EXISTS order_schema.drone_incident_resolution_proposals
(
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES order_schema.drone_parcel_incidents(id),
    proposal_version INTEGER NOT NULL,
    resolution_type VARCHAR(50) NOT NULL,
    redelivery_offered BOOLEAN NOT NULL DEFAULT FALSE,
    compensation_amount NUMERIC(12, 2),
    refund_shipping_fee BOOLEAN NOT NULL DEFAULT FALSE,
    policy_version VARCHAR(64) NOT NULL,
    override_reason VARCHAR(1000),
    proposed_by_user_id BIGINT NOT NULL,
    status VARCHAR(40) NOT NULL,
    customer_response_note VARCHAR(1000),
    responded_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (incident_id, proposal_version)
);

