-- F3-G12: hồ sơ lắp đặt & nghiệm thu tủ. Tủ đi qua PLANNED → INSTALLING → COMMISSIONING → ACTIVE;
-- khách chỉ thấy / đặt được tủ ACTIVE. Bảng mới, không đổi dữ liệu tủ đang chạy.
CREATE TABLE locker_schema.locker_installations (
    id                      BIGSERIAL PRIMARY KEY,
    locker_id               BIGINT       NOT NULL REFERENCES locker_schema.lockers (id),
    status                  VARCHAR(20)  NOT NULL,
    previous_locker_status  VARCHAR(30),
    technician_id           BIGINT       NOT NULL,
    scheduled_at            TIMESTAMP,
    site_contact_name       VARCHAR(100),
    site_contact_phone      VARCHAR(20),
    power_source            VARCHAR(100),
    network_type            VARCHAR(20),
    network_note            VARCHAR(255),
    cabinet_model           VARCHAR(100),
    cabinet_serial          VARCHAR(100),
    notes                   VARCHAR(1000),
    return_reason           VARCHAR(500),
    cancel_reason           VARCHAR(500),
    created_by              BIGINT,
    started_at              TIMESTAMP,
    submitted_at            TIMESTAMP,
    reviewed_by             BIGINT,
    reviewed_at             TIMESTAMP,
    approved_at             TIMESTAMP,
    cancelled_at            TIMESTAMP,
    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP    NOT NULL
);

-- Mỗi tủ chỉ có một hồ sơ lắp đặt đang mở.
CREATE UNIQUE INDEX uq_locker_installations_open
    ON locker_schema.locker_installations (locker_id)
    WHERE status IN ('SCHEDULED', 'IN_PROGRESS', 'SUBMITTED');
CREATE INDEX idx_locker_installations_technician
    ON locker_schema.locker_installations (technician_id, status);

CREATE TABLE locker_schema.locker_installation_checks (
    id               BIGSERIAL PRIMARY KEY,
    installation_id  BIGINT      NOT NULL REFERENCES locker_schema.locker_installations (id) ON DELETE CASCADE,
    item             VARCHAR(30) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    verified_by      VARCHAR(20),
    note             VARCHAR(500),
    detail           TEXT,
    checked_by       BIGINT,
    checked_at       TIMESTAMP,
    CONSTRAINT uq_locker_installation_check UNIQUE (installation_id, item)
);

CREATE TABLE locker_schema.locker_installation_photos (
    id                   BIGSERIAL PRIMARY KEY,
    installation_id      BIGINT        NOT NULL REFERENCES locker_schema.locker_installations (id) ON DELETE CASCADE,
    item                 VARCHAR(30),
    public_id            VARCHAR(255)  NOT NULL UNIQUE,
    secure_url           VARCHAR(1000) NOT NULL,
    format               VARCHAR(20),
    bytes                BIGINT,
    width                INTEGER,
    height               INTEGER,
    caption              VARCHAR(500),
    latitude             DOUBLE PRECISION,
    longitude            DOUBLE PRECISION,
    captured_at          TIMESTAMP,
    uploaded_by_user_id  BIGINT        NOT NULL,
    created_at           TIMESTAMP     NOT NULL
);
CREATE INDEX idx_locker_installation_photos_installation
    ON locker_schema.locker_installation_photos (installation_id);
